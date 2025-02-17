package Com.SimpleFlashSaleBackend.SimpleFlashSale.Controller;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Service.CouponService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    @Autowired
    private final CouponService couponService;

    public CouponController(CouponService couponService){
        this.couponService = couponService;
    }

    // 创建购物券
    @PostMapping
    public ResponseEntity<CouponDTO> createCoupon(@RequestBody @Valid CouponDTO dto) {
        return ResponseEntity.ok(couponService.createCoupon(dto));
    }

    // ✅ 获取所有购物券
    @GetMapping
    public ResponseEntity<List<CouponDTO>> getAllCoupons() {
        return ResponseEntity.ok(couponService.getAllCoupons());
    }

    // ✅ 按名称搜索购物券
    @GetMapping("/search")
    public ResponseEntity<List<CouponDTO>> searchCoupons(@RequestParam String name) {
        return ResponseEntity.ok(couponService.searchCoupons(name));
    }

    // ✅ 更新购物券
    @PutMapping("/{id}")
    public ResponseEntity<CouponDTO> updateCoupon(@PathVariable Long id, @RequestBody @Valid CouponDTO dto) {
        return ResponseEntity.ok(couponService.updateCoupon(id, dto));
    }

    // ✅ 删除购物券
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.noContent().build();
    }

    // ✅ 购买购物券 (Protected Route)
    @PostMapping("/buy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> buyCoupon(@RequestParam Long userId, @RequestParam Long couponId) {
        String response = couponService.buyCoupon(userId, couponId);
        return ResponseEntity.ok(response);
    }
}
