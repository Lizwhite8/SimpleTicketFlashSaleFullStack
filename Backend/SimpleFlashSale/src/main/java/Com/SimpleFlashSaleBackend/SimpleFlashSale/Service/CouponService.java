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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CouponService {
    private final CouponRepository couponRepository;

    private final UserCouponRepository userCouponRepository;

    private final UserRepository userRepository;

    public CouponService(CouponRepository couponRepository, UserCouponRepository userCouponRepository, UserRepository userRepository) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
    }

    // 创建购物券
    public CouponDTO createCoupon(CouponDTO dto) {
        Coupon coupon = CouponMapper.toEntity(dto);
        Coupon savedCoupon = couponRepository.save(coupon);
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

    // 更新购物券
    public CouponDTO updateCoupon(Long id, CouponDTO dto) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + id));

        coupon.setName(dto.getName());
        coupon.setDescription(dto.getDescription());
        coupon.setQuantity(dto.getQuantity());

        Coupon updatedCoupon = couponRepository.save(coupon);
        return CouponMapper.toDTO(updatedCoupon);
    }

    // 删除购物券
    // ✅ Soft delete a coupon
    @Transactional
    public void deleteCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + id));

        coupon.setDeleted(true); // Mark as deleted instead of removing
        couponRepository.save(coupon);
    }

    public void buyCoupon(Long userId, Long couponId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Coupon coupon = couponRepository.findByIdAndIsDeletedFalse(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found or deleted with ID: " + couponId));

        // Check if coupon is available
        if (coupon.getQuantity() <= 0) {
            throw new RuntimeException("Coupon is out of stock!");
        }

        // Decrement the quantity
        coupon.setQuantity(coupon.getQuantity() - 1);
        couponRepository.save(coupon); // Ensure updated quantity is persisted

        // Save user-coupon relation
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUser(user);
        userCoupon.setCoupon(coupon);
        userCouponRepository.save(userCoupon);
    }

}