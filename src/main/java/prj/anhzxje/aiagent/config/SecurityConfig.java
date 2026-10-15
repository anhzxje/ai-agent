package prj.anhzxje.aiagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import prj.anhzxje.aiagent.dto.common.ErrorResponse;
import prj.anhzxje.aiagent.security.CustomUserDetailsService;
import prj.anhzxje.aiagent.security.JwtAuthenticationFilter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cấu hình bảo mật hệ thống (Spring Security 6 & JWT):
 * - Stateless Session: Không lưu session trên server (phù hợp REST API & Electron Desktop).
 * - Phân quyền Endpoint:
 *   + Công khai: /api/auth/** (đăng ký, đăng nhập), /api/system/** (thông số công khai), tệp tĩnh (HTML/CSS/JS).
 *   + Được bảo vệ: Toàn bộ API còn lại yêu cầu JWT Token trong Authorization Header (Bearer <token>).
 * - JWT Filter: Can thiệp trước UsernamePasswordAuthenticationFilter để giải mã và xác thực token.
 * - Xử lý lỗi 401 (Chưa đăng nhập) và 403 (Không có quyền) trả về JSON chuẩn thay vì HTML lỗi mặc định.
 * - CORS: Cho phép Desktop App (Electron) và Web Client kết nối an toàn.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    /**
     * Định nghĩa chuỗi bộ lọc bảo mật chính (Security Filter Chain).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints: Đăng nhập, đăng ký, cấu hình công khai và tài nguyên tĩnh giao diện
                    .requestMatchers("/api/auth/**", "/api/system/**").permitAll()
                    .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/assets/**", "/favicon.ico").permitAll()
                    // Mọi request nghiệp vụ khác bắt buộc phải có JWT Token hợp lệ
                    .anyRequest().authenticated())
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Tùy biến xử lý lỗi bảo mật 401 và 403 thành cấu trúc JSON chuẩn ErrorResponse
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(401)
                                .message("Unauthorized: " + authException.getMessage())
                                .path(request.getRequestURI())
                                .timestamp(LocalDateTime.now())
                                   .build();
                        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                    })
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(403)
                                .message("Forbidden: " + accessDeniedException.getMessage())
                                .path(request.getRequestURI())
                                .timestamp(LocalDateTime.now())
                                .build();
                        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                    }));

        return http.build();
    }

    /**
     * Provider xác thực người dùng dựa trên cơ sở dữ liệu (CustomUserDetailsService + BCrypt).
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Quản lý tiến trình xác thực (AuthenticationManager) cho endpoint đăng nhập.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Thuật toán băm mật khẩu chuẩn BCrypt với Salt ngẫu nhiên bảo mật cao.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cấu hình chia sẻ tài nguyên nguồn gốc chéo (CORS) cho phép Desktop Client kết nối thuận tiện.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
