package prj.anhzxje.aiagent.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đối tượng DTO biểu diễn Công cụ Agent (Agent Tool DTO):
 * Chứa thông tin hiển thị và trạng thái kích hoạt của công cụ (list_directory, glob, file_system, grep).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolDto {
    private String toolName;
    private String displayName;
    private String description;
    private Boolean enabled;
    private String category;
}
