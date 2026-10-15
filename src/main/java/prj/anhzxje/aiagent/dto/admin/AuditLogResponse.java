package prj.anhzxje.aiagent.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO phản hồi Nhật ký Kiểm toán (Audit Log Response):
 * Trả về chi tiết hành vi, người thực hiện, địa chỉ IP và mốc thời gian cho màn hình Kiểm toán của Quản trị viên.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private String username;
    private String action;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;
}
