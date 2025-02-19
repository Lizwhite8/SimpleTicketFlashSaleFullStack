package Com.SimpleFlashSaleBackend.SimpleFlashSale.Controller;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Response.Response;
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
    public ResponseEntity<Response<CouponDTO>> createCoupon(@RequestBody @Valid CouponDTO dto) {
        Response<CouponDTO> response = couponService.createCoupon(dto);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping
    public ResponseEntity<Response<List<CouponDTO>>> getCouponsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {

        Response<List<CouponDTO>> response = couponService.getCouponsPaginated(page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }


    // ✅ Search coupons by name
    @GetMapping("/search")
    public ResponseEntity<Response<List<CouponDTO>>> searchCoupons(@RequestParam String name) {
        Response<List<CouponDTO>> response = couponService.searchCoupons(name);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }


    @PutMapping("/{id}")
    public ResponseEntity<Response<CouponDTO>> updateCoupon(@PathVariable Long id, @RequestBody @Valid CouponDTO dto) {
        Response<CouponDTO> response = couponService.updateCoupon(id, dto);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Response<CouponDTO>> deleteCoupon(@PathVariable Long id) {
        Response<CouponDTO> response = couponService.deleteCoupon(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ✅ 购买购物券 (Protected Route)
    @PostMapping("/buy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Response<String>> buyCoupon(@RequestParam String userId, @RequestParam Long couponId) throws InterruptedException {
        Response<String> response = couponService.buyCoupon(userId, couponId);

        HttpStatus status = (response.getStatusCode() == 200) ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(response);
    }
}
