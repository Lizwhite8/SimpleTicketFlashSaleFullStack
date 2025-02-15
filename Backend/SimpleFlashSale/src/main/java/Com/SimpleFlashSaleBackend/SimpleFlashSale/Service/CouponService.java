package Com.SimpleFlashSaleBackend.SimpleFlashSale.Service;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.Coupon;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Exception.ResourceNotFoundException;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Mapper.CouponMapper;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.CouponRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CouponService {
    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
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
    public void deleteCoupon(Long id) {
        if (!couponRepository.existsById(id)) {
            throw new ResourceNotFoundException("Coupon not found with id: " + id);
        }
        couponRepository.deleteById(id);
    }
}