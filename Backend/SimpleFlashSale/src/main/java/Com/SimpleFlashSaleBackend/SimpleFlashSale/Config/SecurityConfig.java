package Com.SimpleFlashSaleBackend.SimpleFlashSale.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())  // Disable CSRF for testing (enable it later for production)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()   // Allow user registration & login
                        .requestMatchers("/api/coupons/**").permitAll() // ✅ Allow all requests to coupon endpoints
                        .anyRequest().authenticated() // Other requests require authentication
                )
                .formLogin(login -> login.disable()) // Disable default login page
                .httpBasic(basic -> basic.disable()); // Disable HTTP Basic Authentication

        return http.build();
    }
}
