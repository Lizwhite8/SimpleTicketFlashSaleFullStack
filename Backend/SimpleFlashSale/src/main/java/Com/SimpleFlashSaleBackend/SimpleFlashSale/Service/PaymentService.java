package Com.SimpleFlashSaleBackend.SimpleFlashSale.Service;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Controller.WebSocketController;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.Coupon;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.User;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.UserCoupon;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Exception.ResourceNotFoundException;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.CouponRepository;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.UserCouponRepository;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.UserRepository;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PaymentService {
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final WebSocketController webSocketController;

    private final RedissonClient redissonClient;

    private static final String COUPON_CACHE_PREFIX = "SimpleFlashSale#coupon:";

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    public PaymentService(UserRepository userRepository, CouponRepository couponRepository,
                          UserCouponRepository userCouponRepository, RedissonClient redissonClient, WebSocketController webSocketController) {
        this.userRepository = userRepository;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.redissonClient = redissonClient;
        this.webSocketController = webSocketController;
    }

    @KafkaListener(topics = "${kafka.topic.payment}", groupId = "payment-group")
    @Transactional
    public void listenForPayments(String message) {
        processPaymentAsync(message);
    }

    @Async
    public void processPaymentAsync(String message) {
        try {
            // Parse Kafka message
            String[] data = message.split(",");
            Long orderId = Long.parseLong(data[0]);
            Long userId = Long.parseLong(data[1]);
            Long couponId = Long.parseLong(data[2]);

            logger.info("🛒 Processing payment for Order ID: {}", orderId);

            // Notify frontend: Payment processing started
            webSocketController.sendOrderUpdate(orderId, "Payment processing started...");

            // Simulate a payment delay
            Thread.sleep(2000);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + couponId));

            if (user.getCredit() < coupon.getPrice()) {
                logger.error("❌ Payment failed for Order ID: {}, Insufficient credit", orderId);
                webSocketController.sendOrderUpdate(orderId, "Payment failed: Insufficient credit.");

                // Add one back to Redis since the coupon was reserved but not paid
                String couponQuantityKey = COUPON_CACHE_PREFIX + couponId + ":quantity";
                RBucket<String> quantityBucket = redissonClient.getBucket(couponQuantityKey, StringCodec.INSTANCE);
                quantityBucket.set(String.valueOf(Integer.parseInt(quantityBucket.get()) + 1));

                return;
            }

            // Deduct credit and save to MySQL
            user.setCredit(user.getCredit() - coupon.getPrice());
            userRepository.save(user);

            // Save order
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setUser(user);
            userCoupon.setCoupon(coupon);
            userCoupon.setPaymentSuccess(true);
            userCouponRepository.save(userCoupon);

            // Update coupon quantity in MySQL
            synchronized (this) {  // Ensuring thread-safety for updating MySQL
                coupon.setQuantity(coupon.getQuantity() - 1);
                couponRepository.save(coupon);
            }

            // Notify frontend: Payment successful
            logger.info("✅ Payment success for Order ID: {}", orderId);
            webSocketController.sendOrderUpdate(orderId, "Payment successful!");

        } catch (Exception e) {
            logger.error("⚠️ Error processing payment: {}", e.getMessage());
        }
    }
}