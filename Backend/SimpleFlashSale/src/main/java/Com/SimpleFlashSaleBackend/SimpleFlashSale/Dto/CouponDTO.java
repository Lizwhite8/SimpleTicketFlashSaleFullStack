package Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CouponDTO {
    private Long id;
    private String name;
    private String description;
    private int quantity;
}