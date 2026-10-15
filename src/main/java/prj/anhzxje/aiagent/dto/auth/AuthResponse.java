package prj.anhzxje.aiagent.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đối tượng DTO phản hồi xác thực người dùng (Auth Response):
 * Trả về JWT Token, username và quyền hạn (Role) khi đăng nhập hoặc đăng ký thành công.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String role;
    private String message;
}
