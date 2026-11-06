package prj.anhzxje.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "chat_review_links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_review_request", columnNames = {"conversation_id", "request_id"}),
        @UniqueConstraint(name = "uk_chat_review_task", columnNames = "task_id")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatReviewLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conversation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentTask task;
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
}
