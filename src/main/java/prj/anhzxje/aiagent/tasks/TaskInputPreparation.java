package prj.anhzxje.aiagent.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.Project;
import prj.anhzxje.aiagent.skills.SkillTaskSupport;

/** Submission-only input merging. Authorization and persistence belong to the task service. */
public final class TaskInputPreparation {
    private final ObjectMapper objectMapper;

    public TaskInputPreparation(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String prepare(AgentTaskRequest request, Project project, SkillTaskSupport skillTaskSupport) {
        String finalInput = request.getInput();
        try {
            ObjectNode objectNode;
            if (finalInput != null && !finalInput.isBlank()) {
                var tree = objectMapper.readTree(finalInput);
                if (tree == null || !tree.isObject()) throw new IllegalArgumentException("Input phải là đối tượng JSON");
                objectNode = (ObjectNode) tree;
            } else {
                objectNode = objectMapper.createObjectNode();
            }

            // Gắn danh sách công cụ được phép sử dụng vào JSON
            if (request.getSelectedTools() != null) {
                var toolsArr = objectNode.putArray("selectedTools");
                for (String t : request.getSelectedTools()) {
                    toolsArr.add(t);
                }
            }
            // Gắn tên model chỉ định vào JSON
            if (request.getModel() != null && !request.getModel().isBlank()) {
                objectNode.put("model", request.getModel().trim());
            }
            // Gắn prompt bổ sung vào JSON
            if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
                objectNode.put("prompt", request.getPrompt().trim());
            }
            if (skillTaskSupport != null) {
                skillTaskSupport.prepareInput(objectNode, request.getSkills(), project.getLanguage(), project.getName());
            } else if (request.getSkills() != null || objectNode.has("skills")) {
                throw new IllegalArgumentException("Skill Engine chưa được cấu hình");
            }
            finalInput = objectMapper.writeValueAsString(objectNode);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Input phải là một đối tượng JSON hợp lệ", e);
        }

        return finalInput;
    }
}
