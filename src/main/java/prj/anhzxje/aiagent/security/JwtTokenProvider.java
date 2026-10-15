package prj.anhzxje.aiagent.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Thành phần chịu trách nhiệm tạo lập, ký số (Sign) và xác thực (Validate) JSON Web Token (JWT).
 * - Sử dụng thuật toán ký HMAC-SHA với khóa bí mật tối thiểu 256-bit.
 * - Đảm bảo tính toàn vẹn (Integrity) và xác thực danh tính người dùng độc lập (Stateless Authentication) mà không cần session DB.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long jwtExpiration;

    /**
     * Khởi tạo JwtTokenProvider:
     * - Giải mã chuỗi jwtSecret từ định dạng BASE64 sang mảng byte.
     * - Tạo khóa bí mật SecretKey chuẩn thuật toán HMAC-SHA256.
     * - Thiết lập thời gian sống của token (jwtExpiration).
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration}") long jwtExpiration) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.jwtExpiration = jwtExpiration;
    }

    /**
     * Tạo mới một JWT Token có chứa thông tin username và vai trò (Role).
     * Quy trình xử lý:
     * - Bước 1: Xác định mốc thời gian phát hành (issuedAt) và mốc hết hạn (expiration = now + expirationMillis).
     * - Bước 2: Thiết lập Payload:
     *     + Subject (sub): username của người dùng.
     *     + Custom Claim: "role" (ví dụ: ROLE_USER, ROLE_ADMIN) để phân quyền nhanh tại Client/Gateway.
     * - Bước 3: Ký số điện tử bằng khóa bí mật HMAC-SHA256 (key).
     * - Bước 4: Nén và trả về chuỗi JWT chuẩn (dạng header.payload.signature).
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Trích xuất username từ Payload (Subject) của JWT token.
     */
    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Trích xuất Role từ Custom Claims của JWT token.
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * Kiểm tra tính toàn vẹn và hợp lệ của JWT token.
     * Quy trình:
     * - Bước 1: Thử phân tích cú pháp và kiểm tra chữ ký số bằng SecretKey thông qua getClaims().
     * - Bước 2: Bắt các loại ngoại lệ đặc thù của thư viện JJWT:
     *     + MalformedJwtException: Cấu trúc token bị sai lệch hoặc bị can thiệp.
     *     + ExpiredJwtException: Token đã quá thời hạn sử dụng.
     *     + UnsupportedJwtException: Token có định dạng/thuật toán không hỗ trợ.
     *     + IllegalArgumentException: Chuỗi token truyền vào bị null hoặc rỗng.
     * - Bước 3: Trả về true nếu hợp lệ, ngược lại log cảnh báo và trả về false.
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("JWT token không hợp lệ (sai cấu trúc hoặc đã bị sửa đổi): {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string rỗng hoặc null: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Phân tích và trích xuất toàn bộ Payload (Claims) từ chuỗi JWT đã ký.
     * Sử dụng Jwts.parser() với khóa bí mật để xác thực chữ ký (verifyWith).
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
