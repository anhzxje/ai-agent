package prj.anhzxje.aiagent.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import prj.anhzxje.aiagent.enums.Role;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO phản hồi Hồ sơ Người dùng (User Profile Response):
 * Trả về thông tin cá nhân và cấu hình tùy chọn của người dùng đang đăng nhập.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private Boolean isLocked;
    private LocalDateTime createdAt;
    private String language;
    private String theme;
    private String preferredModel;
}
