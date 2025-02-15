package Com.SimpleFlashSaleBackend.SimpleFlashSale.Mapper;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Dto.CouponDTO;
import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.Coupon;

public class CouponMapper {

    // Convert Entity to DTO
    public static CouponDTO toDTO(Coupon coupon) {
        if (coupon == null) {
            return null;
        }

        CouponDTO dto = new CouponDTO();
        dto.setId(coupon.getId());
        dto.setName(coupon.getName());
        dto.setDescription(coupon.getDescription());
        dto.setQuantity(coupon.getQuantity());
        return dto;
    }

    // Convert DTO to Entity
    public static Coupon toEntity(CouponDTO dto) {
        if (dto == null) {
            return null;
        }

        Coupon coupon = new Coupon();
        coupon.setId(dto.getId());
        coupon.setName(dto.getName());
        coupon.setDescription(dto.getDescription());
        coupon.setQuantity(dto.getQuantity());
        return coupon;
    }
}

