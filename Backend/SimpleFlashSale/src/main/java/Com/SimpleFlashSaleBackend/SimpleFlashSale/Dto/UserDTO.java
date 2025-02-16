package Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto;

import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String password;
    private Set<Long> couponIds; // Store IDs of purchased coupons
}
