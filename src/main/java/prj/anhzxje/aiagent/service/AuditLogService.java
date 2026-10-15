package prj.anhzxje.aiagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
     */
    @Transactional
    public void log(String username, String action, String details) {
        log(username, action, details, null);
    }

    /**
     * Ghi nhật ký kiểm toán chi tiết kèm địa chỉ IP client.
     * Thiết kế phòng vệ (Defensive Logging):
     * Khối try-catch bao bọc việc lưu AuditLog để đảm bảo rằng nếu quá trình ghi log gặp sự cố
     * (ví dụ: lỗi kết nối tạm thời, chuỗi chi tiết quá dài...), lỗi này sẽ KHÔNG làm văng ngoại lệ
     * khiến giao dịch chính của người dùng (Đăng ký, Đăng nhập, Tạo Task...) bị rollback ngoài ý muốn.
     */
    @Transactional
    public void log(String username, String action, String details, String ipAddress) {
        try {
            // Bước 1: Khởi tạo thực thể AuditLog, gán giá trị 'ANONYMOUS' nếu chưa xác định danh tính
            AuditLog entry = AuditLog.builder()
                    .username(username != null ? username : "ANONYMOUS")
                    .action(action)
                    .details(details)
                    .ipAddress(ipAddress)
                    .createdAt(LocalDateTime.now())
                    .build();

            // Bước 2: Lưu nhật ký vào bảng audit_logs trong MySQL
            auditLogRepository.save(entry);
            log.info("[AUDIT] User: '{}' | Action: '{}' | Details: '{}'", username, action, details);
        } catch (Exception e) {
            // Bước 3: Nuốt lỗi và ghi log error cục bộ, bảo vệ toàn vẹn luồng nghiệp vụ chính
            log.error("Không thể ghi audit log [user={}, action={}]: {}", username, action, e.getMessage());
        }
    }

    /**
     * Lấy danh sách 100 nhật ký kiểm toán mới nhất phục vụ màn hình Admin.
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

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
