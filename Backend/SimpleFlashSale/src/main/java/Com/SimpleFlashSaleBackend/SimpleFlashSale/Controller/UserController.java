package Com.SimpleFlashSaleBackend.SimpleFlashSale.Controller;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.UserDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Response.Response;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Service.UserService;
import jakarta.validation.Valid;
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
    public ResponseEntity<Response<UserDTO>> registerUser(@RequestBody @Valid UserDTO userDTO) {
        Response<UserDTO> response = userService.registerUser(userDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ✅ User Login (Retrieve Token)
    @PostMapping("/login")
    public ResponseEntity<Response<String>> login(@RequestParam String username, @RequestParam String password) {
        Response<String> response = userService.login(username, password);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ✅ Get user coupons (Protected)
    @GetMapping("/{userId}/coupons")
    @PreAuthorize("isAuthenticated()") // Requires authentication
    public ResponseEntity<Response<List<CouponDTO>>> getUserCoupons(@PathVariable Long userId) {
        Response<List<CouponDTO>> response = userService.getUserCoupons(userId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}

