package prj.anhzxje.aiagent.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Đối tượng DTO phản hồi Cài đặt Hệ thống cho Admin (System Settings Response):
 * Trả về model đang hoạt động, khóa API đã che giấu (masking), danh sách model cho phép và thư mục được phân quyền.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettingsResponse {
    private String activeModel;
    private String maskedApiKey;
    private boolean hasCustomApiKey;
    private List<String> allowedModels;
    private String defaultAllowedPath;
}
