package prj.anhzxje.aiagent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Thực thể Cấu hình Hệ thống (System Settings):
 * Lưu trữ các cài đặt động của ứng dụng vào CSDL (Key-Value):
 * - gemini.model: Model Gemini mặc định đang kích hoạt
 * - gemini.api_key: Khóa API Google Gemini lưu bảo mật trong CSDL
 * - gemini.allowed_models: Danh sách các model cho phép người dùng chọn
 */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(columnDefinition = "TEXT")
    private String settingValue;

    @Column(length = 255)
    private String description;

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
