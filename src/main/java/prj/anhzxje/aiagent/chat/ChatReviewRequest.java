package prj.anhzxje.aiagent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import prj.anhzxje.aiagent.skills.SkillSelection;

@Data
public class ChatReviewRequest {
    @NotBlank @Size(max = 10000) private String message;
    @NotBlank @Size(max = 64) private String requestId;
    private String model;
    private List<String> selectedTools;
    @jakarta.validation.Valid @Size(max = 30) private List<SkillSelection> skills;
}
