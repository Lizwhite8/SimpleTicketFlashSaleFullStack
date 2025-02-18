package Com.SimpleFlashSaleBackend.SimpleFlashSale.Service;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.Coupon;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.User;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.UserCoupon;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Exception.ResourceNotFoundException;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Mapper.CouponMapper;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.CouponRepository;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.UserCouponRepository;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.UserRepository;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Response.Response;

import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CouponService {
    private final CouponRepository couponRepository;
    private final RedissonClient redissonClient;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final Logger logger = LoggerFactory.getLogger(CouponService.class);

    private static final String COUPON_CACHE_PREFIX = "SimpleFlashSale#coupon:";
    private static final String COUPON_UPDATE_LOCK = "SimpleFlashSale#couponUpdate";

    @Value("${kafka.topic.payment}")
    private String paymentTopic;

    public CouponService(CouponRepository couponRepository, RedissonClient redissonClient,
                         UserRepository userRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.couponRepository = couponRepository;
        this.redissonClient = redissonClient;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Create Coupon & Update Redis with Lock */
    @Transactional
    public Response<CouponDTO> createCoupon(CouponDTO dto) {
        RLock lock = redissonClient.getLock(COUPON_UPDATE_LOCK);

        // If another process holds the lock, return 500 response instead of throwing an error
        if (!lock.tryLock()) {
            return new Response<>(500, "Another process is updating coupons. Try again later.", null);
        }

        try {
            Coupon coupon = CouponMapper.toEntity(dto);
            Coupon savedCoupon = couponRepository.save(coupon);
            updateCouponInCache(savedCoupon);

            return new Response<>(200, "Coupon created successfully!", CouponMapper.toDTO(savedCoupon));
        } finally {
            lock.unlock();
        }
    }

    // 按名称搜索购物券
    public Response<List<CouponDTO>> searchCoupons(String name) {
        List<CouponDTO> coupons = couponRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(CouponMapper::toDTO)
                .collect(Collectors.toList());

        if (coupons.isEmpty()) {
            return new Response<>(404, "No coupons found for the given name.", Collections.emptyList());
        }

        return new Response<>(200, "Coupons retrieved successfully!", coupons);
    }

    public Response<List<CouponDTO>> getCouponsPaginated(int page, int size) {
        String redisKey = COUPON_CACHE_PREFIX + "page:" + page + ":size:" + size;
        RBucket<List<CouponDTO>> couponCache = redissonClient.getBucket(redisKey);

        // Try fetching from Redis first
        List<CouponDTO> cachedCoupons = couponCache.get();
        if (cachedCoupons != null) {
            logger.info("✅ Coupons fetched from Redis for page {}: {}", page, cachedCoupons.size());

            // Replace quantity with the latest value from Redis if it exists
//            for (CouponDTO coupon : cachedCoupons) {
//                String couponQuantityKey = COUPON_CACHE_PREFIX + coupon.getId() + ":quantity";
//                RBucket<String> quantityCache = redissonClient.getBucket(couponQuantityKey, StringCodec.INSTANCE);
//                String redisQuantity = quantityCache.get();
//                if (redisQuantity != null) {
//                    coupon.setQuantity(Integer.parseInt(redisQuantity));
//                }
//            }

            return new Response<>(200, "Coupons retrieved successfully from cache!", cachedCoupons);
        }

        // Prevent cache breakdown using lock
        RLock lock = redissonClient.getLock(COUPON_UPDATE_LOCK);
        if (!lock.tryLock()) {
            return new Response<>(500, "Another process is updating the cache. Try again later.", null);
        }

        try {
            logger.warn("⚠️ Coupons not found in Redis, fetching from MySQL...");
            Pageable pageable = PageRequest.of(page, size);
            Page<Coupon> couponPage = couponRepository.findByIsDeletedFalse(pageable);

            List<CouponDTO> coupons = couponPage.getContent().stream()
                    .map(CouponMapper::toDTO)
                    .collect(Collectors.toList());

            if (coupons.isEmpty()) {
                return new Response<>(404, "No coupons available.", Collections.emptyList());
            }

            // Replace quantity with Redis value if available
            for (CouponDTO coupon : coupons) {
                String couponQuantityKey = COUPON_CACHE_PREFIX + coupon.getId() + ":quantity";
                RBucket<String> quantityCache = redissonClient.getBucket(couponQuantityKey, StringCodec.INSTANCE);
                String redisQuantity = quantityCache.get();
                if (redisQuantity != null) {
                    coupon.setQuantity(Integer.parseInt(redisQuantity));
                } else {
                    // If Redis doesn't have quantity, update Redis with DB value
                    quantityCache.set(String.valueOf(coupon.getQuantity()), 5, TimeUnit.MINUTES);
                }
            }

            // Store the paginated result in Redis with a TTL (e.g., 5 minutes)
            couponCache.set(coupons, 2, TimeUnit.MINUTES);
            logger.info("✅ Coupons stored in Redis for page {}.", page);

            return new Response<>(200, "Coupons retrieved successfully!", coupons);
        } finally {
            lock.unlock();
        }
    }

    /** Update Coupon & Sync with Redis using Lock */
    @Transactional
    public Response<CouponDTO> updateCoupon(Long id, CouponDTO dto) {
        RLock lock = redissonClient.getLock(COUPON_UPDATE_LOCK);

        // If another process holds the lock, return 500 response instead of throwing an error
        if (!lock.tryLock()) {
            return new Response<>(500, "Another process is updating coupons. Try again later.", null);
        }

        try {
            Coupon coupon = couponRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));

            coupon.setName(dto.getName());
            coupon.setDescription(dto.getDescription());
            coupon.setQuantity(dto.getQuantity());

            Coupon updatedCoupon = couponRepository.save(coupon);
            updateCouponInCache(updatedCoupon);

            return new Response<>(200, "Coupon updated successfully!", CouponMapper.toDTO(updatedCoupon));
        } finally {
            lock.unlock();
        }
    }

    /** Delete Coupon with Lock */
    @Transactional
    public Response<CouponDTO> deleteCoupon(Long id) {
        RLock lock = redissonClient.getLock(COUPON_UPDATE_LOCK);

        // If another process holds the lock, return a 500 response instead of throwing an error
        if (!lock.tryLock()) {
            return new Response<>(500, "Another process is updating coupons. Try again later.", null);
        }

        try {
            Coupon coupon = couponRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + id));

            coupon.setDeleted(true);
            couponRepository.save(coupon);
            updateCouponInCache(coupon);

            return new Response<>(200, "Coupon deleted successfully!", CouponMapper.toDTO(coupon));
        } finally {
            lock.unlock();
        }
    }

    /** Buy Coupon Using Redis & Lua Script */
    @Transactional
    public Response<String> buyCoupon(Long userId, Long couponId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String couponQuantityKey = COUPON_CACHE_PREFIX + couponId + ":quantity";
        String userOrderSetKey = "SimpleFlashSale#couponUsers:" + couponId;

        RBucket<String> quantityBucket = redissonClient.getBucket(couponQuantityKey, StringCodec.INSTANCE);
        String redisValue = quantityBucket.get();
        logger.info("📌 Redis Value for {}: {}", couponQuantityKey, redisValue);

        RScript script = redissonClient.getScript();
        List<Object> keys = List.of(couponQuantityKey, userOrderSetKey);
        List<Object> args = List.of(userId.toString());

        String luaScript = loadLuaScript("LuaScript/buy_coupon.lua");
        Long result = script.eval(RScript.Mode.READ_WRITE, luaScript, RScript.ReturnType.INTEGER, keys, args);

        if (result == -1) return new Response<>(400, "Coupon out of stock!", null);
        if (result == -2) return new Response<>(400, "User already purchased this coupon!", null);
        if (result == -3) return new Response<>(500, "Redis returned a non-integer for quantity!", null);

        long orderId = System.currentTimeMillis();
        String orderMessage = orderId + "," + userId + "," + couponId;
        kafkaTemplate.send(paymentTopic, orderMessage);
        logger.info("Order sent to Kafka: {}", orderMessage);

        return new Response<>(200, "Order placed successfully!", "Success");
    }

    /** Sync Coupon Data to Redis */
    private void updateCouponInCache(Coupon coupon) {
        String redisKey = COUPON_CACHE_PREFIX + coupon.getId();
        String quantityKey = redisKey + ":quantity";

//        RBucket<Coupon> couponCache = redissonClient.getBucket(redisKey);
        RBucket<String> quantityCache = redissonClient.getBucket(quantityKey, StringCodec.INSTANCE);

        if (!coupon.isDeleted()) {
//            couponCache.set(coupon);
            quantityCache.set(String.valueOf(coupon.getQuantity()));
            logger.info("✅ Coupon stored in Redis: {}, Quantity: {}", coupon, coupon.getQuantity());
        } else {
//            couponCache.delete();
            quantityCache.delete();
            logger.info("❌ Coupon deleted from Redis: {}", coupon.getId());
        }
    }

    public String loadLuaScript(String scriptPath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(scriptPath)) {
            if (inputStream == null) throw new RuntimeException("Lua script not found: " + scriptPath);
            return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script: " + scriptPath, e);
        }
    }
}