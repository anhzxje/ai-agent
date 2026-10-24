package prj.anhzxje.aiagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import prj.anhzxje.aiagent.dto.task.AgentTaskRequest;
import prj.anhzxje.aiagent.dto.task.AgentTaskResponse;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.service.AgentTaskService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiagentApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:application-web;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.ai.google.genai.api-key=test-no-network",
        "bootstrap.admin-password=", "agent.allowed-paths[0]=."
})
class ApplicationWebIntegrationTest {
    @Autowired WebApplicationContext context;
    @MockitoBean AgentTaskService tasks;

    @Test void actualHttpConverterAcceptsObjectInput() throws Exception {
        when(tasks.createTask(any())).thenReturn(AgentTaskResponse.builder().id(1L).build());
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var principal = new CustomUserDetails(User.builder().id(1L).username("tester").password("hash").build());
        mvc.perform(post("/api/tasks").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":1,\"type\":\"CODE_REVIEW\",\"input\":{\"focus\":\"security\"}}"))
                .andExpect(status().isCreated());
        var captured = org.mockito.ArgumentCaptor.forClass(AgentTaskRequest.class);
        verify(tasks).createTask(captured.capture());
        org.junit.jupiter.api.Assertions.assertEquals("{\"focus\":\"security\"}", captured.getValue().getInput());
    }
}
