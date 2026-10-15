package prj.anhzxje.aiagent.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Đối tượng DTO yêu cầu Cập nhật Cài đặt Hệ thống từ Admin (System Settings Update Request):
 * Cho phép quản trị viên thay đổi model Gemini hoạt động, cập nhật API Key hoặc cập nhật danh sách model cho phép.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettingsUpdateRequest {
    private String model;
    private String apiKey;
    private List<String> allowedModels;
}
