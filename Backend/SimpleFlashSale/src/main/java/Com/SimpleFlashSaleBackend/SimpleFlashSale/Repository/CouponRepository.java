package Com.SimpleFlashSaleBackend.SimpleFlashSale.Repository;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    List<Coupon> findByNameContainingIgnoreCase(String name);
}

