package prj.anhzxje.aiagent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.TaskType;

import java.time.LocalDateTime;

/**
 * Thực thể Tác vụ AI Agent (Agent Task):
 * Lưu trữ tiến trình thực thi tác vụ phân tích mã nguồn giữa User, Project và LLM:
 * - Trạng thái vòng đời: PENDING -> PROCESSING -> COMPLETED / ERROR / CANCELLED
 * - Input JSON: Tham số cấu hình tác vụ (projectPath, model, focus, selectedTools, prompt)
 * - Output JSON: Báo cáo đánh giá trả về từ AI Agent (summary, issues, suggestions)
 * - isPinned: Ghim tác vụ quan trọng lên thanh điều hướng bên trái
 */
@Entity
@Table(name = "agent_tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Liên kết trực tiếp tới User — cho phép query "Task của tôi"
     * mà không cần đi qua Project.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Liên kết tới Project mà task này thuộc về.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * Loại task: CODE_REVIEW, CODE_FIX, CODE_EXPLAIN, TEST_GENERATION, ...
     * Thêm TaskType mới không cần thay đổi schema.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskType type;

    /**
     * Trạng thái lifecycle: PENDING → PROCESSING → COMPLETED / ERROR
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    /**
     * Input dạng JSON — linh hoạt cho mọi loại task.
     * Ví dụ CODE_REVIEW: {"projectPath": "...", "files": [...]}
     * Ví dụ CODE_FIX:    {"file": "...", "issueId": "..."}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String input;

    /**
     * Output dạng JSON — kết quả từ AI Agent.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String output;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    @Column(name = "is_pinned")
    @Builder.Default
    private Boolean isPinned = false;
}
