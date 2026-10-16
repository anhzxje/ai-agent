package prj.anhzxje.aiagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.dto.admin.AuditLogResponse;
import prj.anhzxje.aiagent.entity.AuditLog;
import prj.anhzxje.aiagent.repository.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dịch vụ Nhật ký Kiểm toán Hệ thống (AuditLogService):
 * Chịu trách nhiệm:
 * 1. Ghi nhận các hành vi nhạy cảm (đăng nhập, đổi mật khẩu, đổi cấu hình model, bật tắt công cụ, chạy review).
 * 2. Lưu trữ vết kiểm toán vào bảng audit_logs trong MySQL để phục vụ việc giám sát an toàn thông tin.
 * 3. Cung cấp API truy vấn 100 sự kiện kiểm toán gần nhất cho Quản trị viên (ADMIN).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Ghi nhật ký kiểm toán cơ bản (không kèm IP client).
     *
     * @param username tên tài khoản người thực hiện hành động
     * @param action   mã hành động nghiệp vụ (LOGIN, REGISTER, CREATE_TASK, UPDATE_ROLE, ...)
     * @param details  chi tiết mô tả nội dung hành động
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, String action, String details) {
        log(username, action, details, null);
    }

    /**
     * Ghi nhật ký kiểm toán chi tiết kèm địa chỉ IP client.
     * Thiết kế phòng vệ (Defensive Logging):
     * 1. Áp dụng Propagation.REQUIRES_NEW để việc ghi audit log diễn ra trong một sub-transaction hoàn toàn
     *    độc lập. Nếu xảy ra sự cố trong lúc ghi log, nó tuyệt đối KHÔNG làm đánh dấu rollback giao dịch của
     *    luồng nghiệp vụ chính của người dùng (Đăng ký, Đăng nhập, Tạo Task, Đổi mật khẩu...).
     * 2. Tự động cắt ngắn (Sanitize/Truncate) nếu độ dài chuỗi vượt quá schema CSDL để chống lỗi DataTruncation.
     *
     * @param username  tên tài khoản người thực hiện hành động
     * @param action    mã hành động nghiệp vụ
     * @param details   chi tiết mô tả nội dung hành động
     * @param ipAddress địa chỉ IP của client gửi yêu cầu (có thể null)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, String action, String details, String ipAddress) {
        try {
            // Cắt ngắn an toàn để không vượt kích thước cột database (length = 50 cho username/action, 2000 cho details)
            String safeUsername = username != null ? (username.length() > 50 ? username.substring(0, 50) : username) : "ANONYMOUS";
            String safeAction = action != null ? (action.length() > 50 ? action.substring(0, 50) : action) : "UNKNOWN";
            String safeDetails = details;
            if (safeDetails != null && safeDetails.length() > 2000) {
                safeDetails = safeDetails.substring(0, 1997) + "...";
            }

            // Bước 1: Khởi tạo thực thể AuditLog
            AuditLog entry = AuditLog.builder()
                    .username(safeUsername)
                    .action(safeAction)
                    .details(safeDetails)
                    .ipAddress(ipAddress)
                    .createdAt(LocalDateTime.now())
                    .build();

            // Bước 2: Lưu nhật ký vào bảng audit_logs trong MySQL
            auditLogRepository.save(entry);
            log.info("[AUDIT] User: '{}' | Action: '{}' | Details: '{}'", safeUsername, safeAction, safeDetails);
        } catch (Exception e) {
            // Bước 3: Nuốt lỗi và ghi log error cục bộ, bảo vệ toàn vẹn luồng nghiệp vụ chính
            log.error("Không thể ghi audit log [user={}, action={}]: {}", username, action, e.getMessage());
        }
    }

    /**
     * Lấy danh sách 100 nhật ký kiểm toán mới nhất phục vụ màn hình Quản trị viên (Admin Audit Log).
     *
     * @return danh sách AuditLogResponse gần đây nhất
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Chuyển đổi thực thể AuditLog sang DTO AuditLogResponse.
     *
     * @param log thực thể AuditLog nguồn
     * @return AuditLogResponse DTO
     */
    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .username(log.getUsername())
                .action(log.getAction())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
