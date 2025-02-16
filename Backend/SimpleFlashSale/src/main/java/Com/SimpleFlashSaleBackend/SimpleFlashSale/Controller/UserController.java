package Com.SimpleFlashSaleBackend.SimpleFlashSale.Controller;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.UserDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ✅ User Registration (No admin token needed)
    @PostMapping("/register")
    public ResponseEntity<UserDTO> registerUser(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(userService.registerUser(userDTO));
    }

    // ✅ User Login (Retrieve Token)
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username, @RequestParam String password) {
        String token = userService.login(username, password);
        return ResponseEntity.ok("Bearer " + token);
    }

    // ✅ Get user coupons (Protected)
    @GetMapping("/{userId}/coupons")
    @PreAuthorize("isAuthenticated()") // Requires authentication
    public ResponseEntity<List<CouponDTO>> getUserCoupons(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserCoupons(userId));
    }
}
