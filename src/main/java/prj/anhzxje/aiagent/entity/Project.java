package prj.anhzxje.aiagent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Thực thể Dự án Mã nguồn (Project):
 * Đại diện cho một dự án/thư mục mã nguồn mà người dùng đã đăng ký vào hệ thống để AI Agent phân tích.
 * Ánh xạ tới bảng `projects` trong CSDL MySQL.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String path;

    @Column(length = 50)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Callback JPA tự động làm mới thời điểm updatedAt khi có thay đổi thông tin dự án.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
