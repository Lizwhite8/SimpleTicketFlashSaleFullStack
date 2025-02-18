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
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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

    @Value("${kafka.topic.payment}")
    private String paymentTopic;

    public CouponService(CouponRepository couponRepository, RedissonClient redissonClient,
                         UserRepository userRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.couponRepository = couponRepository;
        this.redissonClient = redissonClient;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Create Coupon & Update Redis */
    public CouponDTO createCoupon(CouponDTO dto) {
        Coupon coupon = CouponMapper.toEntity(dto);
        Coupon savedCoupon = couponRepository.save(coupon);
        updateCouponInCache(savedCoupon);
        return CouponMapper.toDTO(savedCoupon);
    }

    // 获取所有购物券
    public List<CouponDTO> getAllCoupons() {
        return couponRepository.findAll().stream()
                .map(CouponMapper::toDTO)
                .collect(Collectors.toList());
    }

    // 按名称搜索购物券
    public List<CouponDTO> searchCoupons(String name) {
        return couponRepository.findByNameContainingIgnoreCase(name).stream()
                .map(CouponMapper::toDTO)
                .collect(Collectors.toList());
    }

    /** Update Coupon & Sync with Redis */
    public CouponDTO updateCoupon(Long id, CouponDTO dto) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));

        coupon.setName(dto.getName());
        coupon.setDescription(dto.getDescription());
        coupon.setQuantity(dto.getQuantity());

        Coupon updatedCoupon = couponRepository.save(coupon);
        updateCouponInCache(updatedCoupon);
        return CouponMapper.toDTO(updatedCoupon);
    }

    /** Sync Coupon Data to Redis */
    private void updateCouponInCache(Coupon coupon) {
        String redisKey = COUPON_CACHE_PREFIX + coupon.getId();
        String quantityKey = redisKey + ":quantity";

        // Store Coupon object (will use Redisson's default serialization)
        RBucket<Coupon> couponCache = redissonClient.getBucket(redisKey);

        // Store quantity as a plain string
        RBucket<String> quantityCache = redissonClient.getBucket(quantityKey, StringCodec.INSTANCE);

        if (!coupon.isDeleted()) {
            couponCache.set(coupon);

            // Ensure quantity is stored as a string
            quantityCache.set(String.valueOf(coupon.getQuantity()));

            logger.info("✅ Coupon stored in Redis: {}, Quantity: {}", coupon, coupon.getQuantity());
        } else {
            couponCache.delete();
            quantityCache.delete();
            logger.info("❌ Coupon deleted from Redis: {}", coupon.getId());
        }
    }


    // 删除购物券
    // ✅ Soft delete a coupon
    @Transactional
    public void deleteCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + id));

        coupon.setDeleted(true); // Mark as deleted instead of removing
        couponRepository.save(coupon);
        logger.warn("⚠️ Coupon marked as deleted in DB: {}", id);

        updateCouponInCache(coupon); // Ensure it's also removed from Redis
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
        List<Object> keys = Arrays.asList(couponQuantityKey, userOrderSetKey);
        List<Object> args = Arrays.asList(userId.toString());

        String luaScript = loadLuaScript("LuaScript/buy_coupon.lua");
        Long result = script.eval(RScript.Mode.READ_WRITE, luaScript, RScript.ReturnType.INTEGER, keys, args);

        if (result == -1) {
            return new Response<>(400, "Coupon out of stock!", null);
        }
        if (result == -2) {
            return new Response<>(400, "User already purchased this coupon!", null);
        }
        if (result == -3) {
            return new Response<>(500, "⚠️ Redis returned a non-integer for quantity!", null);
        }

        long orderId = System.currentTimeMillis();
        String orderMessage = orderId + "," + userId + "," + couponId;
        kafkaTemplate.send(paymentTopic, orderMessage);
        logger.info("✅ Order sent to Kafka: {}", orderMessage);

        return new Response<>(200, "Order placed successfully!", "Success");
    }
    public String loadLuaScript(String scriptPath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(scriptPath)) {
            if (inputStream == null) {
                throw new RuntimeException("Lua script not found in resources: " + scriptPath);
            }
            return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script: " + scriptPath, e);
        }
    }
}