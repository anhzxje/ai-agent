package prj.anhzxje.aiagent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Thực thể Nhật ký Kiểm toán (Audit Log):
 * Lưu vết mọi hoạt động quan trọng trong hệ thống:
 * - Đăng nhập, đăng xuất, đổi mật khẩu
 * - Khóa / mở khóa tài khoản
 * - Bật / tắt công cụ Agent
 * - Thay đổi model và cập nhật API Key
 * - Tạo, hủy, xóa tác vụ AI
 * Chỉ có vai trò ROLE_ADMIN mới có quyền truy cập xem bảng này.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 2000)
    private String details;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
