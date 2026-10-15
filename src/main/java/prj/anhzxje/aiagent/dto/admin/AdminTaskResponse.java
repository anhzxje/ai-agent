package prj.anhzxje.aiagent.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.TaskType;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO Giám sát Tác vụ Hệ thống (Admin Task Response):
 * Trả về chi tiết tác vụ của bất kỳ người dùng nào kèm thông tin người tạo (username, email) phục vụ Quản trị viên.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTaskResponse {
    private Long id;
    private Long userId;
    private String username;
    private String userEmail;
    private Long projectId;
    private String projectName;
    private TaskType type;
    private JobStatus status;
    private String input;
    private String output;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Boolean isPinned;
}
