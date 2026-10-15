package prj.anhzxje.aiagent.dto.task;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.TaskType;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO phản hồi chi tiết Tác vụ AI (Agent Task Response):
 * Trả về thông tin trạng thái thực thi, kết quả review/fix và mã lỗi nếu có.
 * Sử dụng @JsonRawValue để output và input dạng chuỗi JSON thô được serialize thẳng thành JSON object thực thụ.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentTaskResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private TaskType type;
    private JobStatus status;

    @JsonRawValue
    private String input;

    @JsonRawValue
    private String output;

    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Boolean isPinned;
}
