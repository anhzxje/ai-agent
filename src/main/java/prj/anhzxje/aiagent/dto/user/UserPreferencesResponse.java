package prj.anhzxje.aiagent.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO phản hồi Tùy chọn Cá nhân (User Preferences Response):
 * Trả về các thiết lập ngôn ngữ, theme và preferredModel của người dùng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesResponse {
    private String language;
    private String theme;
    private String preferredModel;
    private LocalDateTime updatedAt;
}
