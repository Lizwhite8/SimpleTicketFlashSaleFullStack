package Com.SimpleFlashSaleBackend.SimpleFlashSale.Service;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.UserDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.User;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Mapper.UserMapper;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.UserRepository;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Response.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    public UserService(UserRepository userRepository, KeycloakService keycloakService) {
        this.userRepository = userRepository;
        this.keycloakService = keycloakService;
    }

    // ✅ Register user (No admin token needed)
    @Transactional
    public Response<UserDTO> registerUser(UserDTO userDTO) {
        try {
            // Register in Keycloak
            keycloakService.registerUser(userDTO);

            // Save user in database with default credit
            userDTO.setCredit(100f);
            User user = UserMapper.toEntity(userDTO);
            User savedUser = userRepository.save(user);

            return new Response<>(200, "User registered successfully!", UserMapper.toDTO(savedUser));
        } catch (Exception e) {
            return new Response<>(500, "User registration failed: " + e.getMessage(), null);
        }
    }

    // ✅ User Login (Retrieve Token)
    public Response<String> login(String username, String password) {
        try {
            String token = keycloakService.getUserToken(username, password);
            return new Response<>(200, "Login successful!", "Bearer " + token);
        } catch (Exception e) {
            return new Response<>(401, "Invalid credentials!", null);
        }
    }

    @Transactional
    public Response<List<CouponDTO>> getUserCoupons(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<CouponDTO> coupons = user.getUserCoupons().stream()
                .map(userCoupon -> new CouponDTO(
                        userCoupon.getCoupon().getId(),
                        userCoupon.getCoupon().getName(),
                        userCoupon.getCoupon().getImageURL(),
                        userCoupon.getCoupon().getPrice(),
                        userCoupon.getCoupon().getDescription(),
                        userCoupon.getCoupon().getQuantity(),
                        userCoupon.getCoupon().isDeleted()
                ))
                .collect(Collectors.toList());

        if (coupons.isEmpty()) {
            return new Response<>(404, "No coupons found for this user.", Collections.emptyList());
        }

        return new Response<>(200, "User coupons retrieved successfully!", coupons);
    }
}
