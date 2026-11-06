package prj.anhzxje.aiagent.tasks;

import prj.anhzxje.aiagent.review.ReviewReportValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import prj.anhzxje.aiagent.review.CodeReviewAgent;
import prj.anhzxje.aiagent.infrastructure.configuration.AgentProperties;
import prj.anhzxje.aiagent.shared.enums.JobStatus;
import prj.anhzxje.aiagent.shared.enums.TaskType;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.infrastructure.filesystem.ProjectPathPolicy;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runs a claimed task outside database transactions during the AI call. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskExecutor {
    private prj.anhzxje.aiagent.skills.SkillTaskSupport skillTaskSupport;

    @org.springframework.beans.factory.annotation.Autowired
    void configureSkills(prj.anhzxje.aiagent.skills.SkillTaskSupport support) { this.skillTaskSupport = support; }

    private final CodeReviewAgent codeReviewAgent;
    private final AgentTaskRepository agentTaskRepository;
    private final ObjectMapper objectMapper;
    private List<String> allowedPaths = List.of();

    @org.springframework.beans.factory.annotation.Autowired
    void configureAgentPaths(AgentProperties properties) {
        allowedPaths = properties.getAllowedPaths();
    }

    @Async("agentThreadPoolExecutor")
    public void executeTaskAsync(Long taskId, String projectPath, String inputJson, TaskType taskType) {
        String executionId = UUID.randomUUID().toString();
        // Only one worker may claim a pending task. Deleted/cancelled tasks are skipped.
        if (agentTaskRepository.claimTask(taskId, executionId) == 0) return;
        try {
            if (taskType != TaskType.CODE_REVIEW) {
                throw new IllegalArgumentException("Loại tác vụ chưa được hỗ trợ: " + taskType);
            }
            Path base = ProjectPathPolicy.requireAllowedDirectory(projectPath, allowedPaths);
            String focus = null;
            String model = null;
            List<String> selectedTools = null;
            var skillContext = prj.anhzxje.aiagent.skills.ReviewSkillContext.legacy();
            boolean skillSelectionPresent = false;
            if (inputJson != null && !inputJson.isBlank()) {
                JsonNode input = objectMapper.readTree(inputJson);
                if (input == null || !input.isObject()) {
                    throw new IllegalArgumentException("Input phải là một đối tượng JSON");
                }
                skillSelectionPresent = input.has("skills");
                if (skillSelectionPresent) {
                    if (skillTaskSupport == null) throw new IllegalArgumentException("Skill Engine chưa được cấu hình");
                    skillContext = skillTaskSupport.readContext(input);
                }
                focus = text(input, "focus");
                String prompt = text(input, "prompt");
                if (prompt != null) focus = focus == null ? prompt : focus + "\n" + prompt;
                model = text(input, "model");
                JsonNode tools = input.has("selectedTools") ? input.get("selectedTools") : input.get("tools");
                if (tools != null) {
                    if (!tools.isArray()) throw new IllegalArgumentException("selectedTools phải là danh sách");
                    selectedTools = new ArrayList<>();
                    for (JsonNode tool : tools) {
                        if (!tool.isTextual() || tool.asText().isBlank()) {
                            throw new IllegalArgumentException("Tên công cụ không hợp lệ");
                        }
                        selectedTools.add(tool.asText());
                    }
                }
                String override = text(input, "projectPath");
                if (override != null) {
                    Path requested = ProjectPathPolicy.requireAllowedDirectory(override, allowedPaths);
                    if (!requested.startsWith(base)) {
                        throw new SecurityException("Đường dẫn nằm ngoài dự án hiện tại");
                    }
                    base = requested;
                }
            }
            String result = codeReviewAgent.reviewPath(base.toString(), focus, selectedTools, model, skillContext,
                    () -> agentTaskRepository.existsByIdAndStatusAndExecutionId(taskId, JobStatus.PROCESSING, executionId));
            JsonNode output = result == null ? null : objectMapper.readTree(result);
            var report = new ReviewReportValidator().validate(output);
            report.remove("appliedSkills"); // Provenance is server-owned, never accepted from the model.
            if (skillSelectionPresent) report.set("appliedSkills", objectMapper.valueToTree(
                    skillContext.skills().stream().map(prj.anhzxje.aiagent.skills.SkillDefinition::selection).toList()));
            agentTaskRepository.finishTask(taskId, executionId, JobStatus.COMPLETED,
                    objectMapper.writeValueAsString(output), null, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Lỗi khi thực thi task {}", taskId, e);
            String message = e.getMessage() == null ? "Không thể thực thi tác vụ AI" : e.getMessage();
            // Match the column length so reporting an AI error cannot itself fail.
            if (message.length() > 1000) message = message.substring(0, 1000);
            agentTaskRepository.finishTask(taskId, executionId, JobStatus.ERROR, null, message, LocalDateTime.now());
        }
    }

    private static String text(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException(field + " phải là chuỗi");
        return value.asText().isBlank() ? null : value.asText().trim();
    }
}
