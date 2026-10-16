package prj.anhzxje.aiagent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Thực thể Cuộc Hội thoại với AI (Chat Conversation):
 * Đại diện cho một phiên làm việc / trao đổi giữa người dùng và AI Agent.
 * Ánh xạ tới bảng `chat_conversations` trong CSDL MySQL.
 */
@Entity
@Table(name = "chat_conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 50)
    @Builder.Default
    private String model = "gemini-3.5-flash-lite";

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Callback JPA tự động làm mới thời điểm updatedAt khi có tin nhắn mới hoặc cập nhật tiêu đề hội thoại.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
