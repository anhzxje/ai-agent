package prj.anhzxje.aiagent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Thực thể Cấu hình Công cụ của AI Agent (Agent Tool Config):
 * Ánh xạ tới bảng `agent_tool_configs` trong CSDL MySQL.
 * Lưu trữ trạng thái bật/tắt (enabled) và siêu dữ liệu của các công cụ mà Agent có thể gọi:
 * - list_directory: Duyệt cây thư mục
 * - glob: Tìm file theo mẫu regex/wildcard
 * - file_system: Đọc mã nguồn tệp tin
 * - grep: Tìm kiếm từ khóa trên toàn bộ dự án
 */
@Entity
@Table(name = "agent_tool_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String toolName;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(length = 50)
    @Builder.Default
    private String category = "CORE";

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
