package prj.anhzxje.aiagent;

import org.junit.jupiter.api.Test;
import prj.anhzxje.aiagent.chat.ChatResponses;
import prj.anhzxje.aiagent.projects.ProjectResponses;
import prj.anhzxje.aiagent.tasks.TaskResponses;
import prj.anhzxje.aiagent.chat.ChatConversationResponse;
import prj.anhzxje.aiagent.projects.ProjectResponse;
import prj.anhzxje.aiagent.tasks.AgentTaskResponse;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.AgentTask;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatConversation;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.Project;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.User;
import prj.anhzxje.aiagent.shared.enums.JobStatus;
import prj.anhzxje.aiagent.shared.enums.TaskType;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ResponseMappingTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0);
    private final Project project = Project.builder().id(2L).name("App").description("API").path("/workspace/app")
            .language("Java").user(User.builder().username("alice").build()).createdAt(now).updatedAt(now).build();

    @Test void projectMappingPreservesEveryPublicFieldAndNullableOwner() {
        assertEquals(ProjectResponse.builder().id(2L).name("App").description("API").path("/workspace/app")
                .language("Java").ownerUsername("alice").createdAt(now).updatedAt(now).build(), ProjectResponses.from(project));
        project.setUser(null);
        assertNull(ProjectResponses.from(project).getOwnerUsername());
    }

    @Test void taskMappingPreservesRawJsonAndNormalizesOnlyPinnedFlag() {
        var task = AgentTask.builder().id(3L).project(project).type(TaskType.CODE_REVIEW).status(JobStatus.COMPLETED)
                .input("{\"focus\":\"security\"}").output("{\"summary\":\"ok\"}").errorMessage("detail")
                .createdAt(now).completedAt(now).isPinned(null).build();
        assertEquals(AgentTaskResponse.builder().id(3L).projectId(2L).projectName("App").type(TaskType.CODE_REVIEW)
                .status(JobStatus.COMPLETED).input(task.getInput()).output(task.getOutput()).errorMessage("detail")
                .createdAt(now).completedAt(now).isPinned(false).build(), TaskResponses.from(task));
        task.setProject(null); task.setIsPinned(true);
        var response = TaskResponses.from(task);
        assertNull(response.getProjectId()); assertNull(response.getProjectName()); assertTrue(response.getIsPinned());
    }

    @Test void conversationMappingPreservesModelAndNullableProject() {
        var conversation = ChatConversation.builder().id(4L).title("Title").model("model").project(project)
                .createdAt(now).updatedAt(now).build();
        assertEquals(ChatConversationResponse.builder().id(4L).title("Title").model("model").projectId(2L)
                .projectName("App").createdAt(now).updatedAt(now).build(), ChatResponses.conversation(conversation));
        conversation.setProject(null);
        assertNull(ChatResponses.conversation(conversation).getProjectId());
        assertNull(ChatResponses.conversation(conversation).getProjectName());
    }
}
