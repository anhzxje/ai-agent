package prj.anhzxje.aiagent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Thực thể Tùy chọn Cá nhân của Người dùng (User Preferences):
 * Lưu trữ cấu hình giao diện và sở thích của từng user:
 * - language: Ngôn ngữ hiển thị (vi / en)
 * - theme: Chế độ giao diện (dark / light)
 * - preferredModel: Model Gemini ưa thích mặc định cho người dùng này
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String language = "vi";

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String theme = "dark";

    @Column(length = 50)
    @Builder.Default
    private String preferredModel = "gemini-3.5-flash-lite";

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
