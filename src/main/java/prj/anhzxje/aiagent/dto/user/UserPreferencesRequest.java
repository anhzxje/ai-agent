package prj.anhzxje.aiagent.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đối tượng DTO yêu cầu Cập nhật Tùy chọn Cá nhân (User Preferences Request):
 * Gửi các thiết lập giao diện và model Gemini ưa thích lên server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesRequest {
    private String language;
    private String theme;
    private String preferredModel;
}
