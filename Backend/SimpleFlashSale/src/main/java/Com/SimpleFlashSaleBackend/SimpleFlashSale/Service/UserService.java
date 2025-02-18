package Com.SimpleFlashSaleBackend.SimpleFlashSale.Service;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.UserDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.User;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Mapper.UserMapper;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public UserDTO registerUser(UserDTO userDTO) {
        // Register in Keycloak
        keycloakService.registerUser(userDTO);

        // Save user in database
        userDTO.setCredit((float)100);
        User user = UserMapper.toEntity(userDTO);
        User savedUser = userRepository.save(user);

        return UserMapper.toDTO(savedUser);
    }

    // ✅ User Login (Retrieve Token)
    public String login(String username, String password) {
        return keycloakService.getUserToken(username, password);
    }

    @Transactional
    public List<CouponDTO> getUserCoupons(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return user.getUserCoupons().stream()
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
    }
}