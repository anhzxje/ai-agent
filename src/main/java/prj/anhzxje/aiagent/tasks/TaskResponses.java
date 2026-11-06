package prj.anhzxje.aiagent.tasks;

import prj.anhzxje.aiagent.infrastructure.persistence.entity.AgentTask;

/** Canonical response mapping shared by owner and administration entry points. No authorization decisions. */
public final class TaskResponses {
    private TaskResponses() {}

    public static AgentTaskResponse from(AgentTask task) {
        return AgentTaskResponse.builder()
                .id(task.getId())
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .projectName(task.getProject() != null ? task.getProject().getName() : null)
                .type(task.getType())
                .status(task.getStatus())
                .input(task.getInput())
                .output(task.getOutput())
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .isPinned(Boolean.TRUE.equals(task.getIsPinned()))
                .build();
    }
}
