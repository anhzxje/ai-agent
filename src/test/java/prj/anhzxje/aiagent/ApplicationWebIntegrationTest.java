package prj.anhzxje.aiagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import prj.anhzxje.aiagent.tasks.AgentTaskRequest;
import prj.anhzxje.aiagent.tasks.AgentTaskResponse;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.User;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.tasks.AgentTaskService;

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
    @Test void skillCatalogRequiresAuthenticationAndReturnsMetadataOnly() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/skills"))
            .andExpect(status().isUnauthorized());
        var principal = new CustomUserDetails(User.builder().id(1L).username("tester").password("hash").build());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/skills").with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(5))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").value("security-review"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].version").value("1.0.0"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].content").doesNotExist());
    }
    @Test void actualHttpConverterAcceptsVersionedSkills() throws Exception {
        when(tasks.createTask(any())).thenReturn(AgentTaskResponse.builder().id(2L).build());
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var principal = new CustomUserDetails(User.builder().id(1L).username("tester").password("hash").build());
        mvc.perform(post("/api/tasks").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"projectId\":1,\"type\":\"CODE_REVIEW\",\"input\":{\"focus\":\"security\"},\"skills\":[{\"id\":\"security-review\",\"version\":\"1.0.0\"}]}"))
            .andExpect(status().isCreated());
        var captured = org.mockito.ArgumentCaptor.forClass(AgentTaskRequest.class);
        verify(tasks).createTask(captured.capture());
        org.junit.jupiter.api.Assertions.assertEquals("security-review", captured.getValue().getSkills().getFirst().id());
        org.junit.jupiter.api.Assertions.assertEquals("1.0.0", captured.getValue().getSkills().getFirst().version());
    }
    @Test void adminEndpointsEnforceRoleWithRealSecurityFilters() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var developer = new CustomUserDetails(User.builder().id(2L).username("developer")
                .password("hash").role(prj.anhzxje.aiagent.shared.enums.Role.USER).build());
        for (String endpoint : java.util.List.of("stats", "users", "tools", "settings", "tasks", "audit-logs")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/" + endpoint))
                    .andExpect(status().isUnauthorized());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/" + endpoint)
                    .with(user(developer))).andExpect(status().isForbidden());
        }
        var admin = new CustomUserDetails(User.builder().id(1L).username("administrator")
                .password("hash").role(prj.anhzxje.aiagent.shared.enums.Role.ADMIN).build());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/stats")
                .with(user(admin))).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/settings")
                .with(user(admin))).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.apiKey").doesNotExist());
    }

    @Test void reviewRequestContractRejectsDisplayMetadataAndAcceptsApiPayload() throws Exception {
        when(tasks.createTask(any())).thenReturn(AgentTaskResponse.builder().id(3L).build());
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var principal = new CustomUserDetails(User.builder().id(1L).username("tester").password("hash").build());
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = java.util.Map.of("projectId", 1, "projectName", "services aiagent", "type", "CODE_REVIEW",
                "input", json.writeValueAsString(java.util.Map.of("projectPath", "/workspace/service", "focus", "all")),
                "selectedTools", java.util.List.of("file_system"),
                "skills", java.util.List.of(java.util.Map.of("id", "security-review", "version", "1.0.0")));
        mvc.perform(post("/api/tasks").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        var apiPayload = new java.util.LinkedHashMap<>(payload);
        apiPayload.remove("projectName");
        mvc.perform(post("/api/tasks").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(apiPayload))).andExpect(status().isCreated());
        var captured = org.mockito.ArgumentCaptor.forClass(AgentTaskRequest.class);
        verify(tasks).createTask(captured.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1L, captured.getValue().getProjectId());
        org.junit.jupiter.api.Assertions.assertEquals("security-review", captured.getValue().getSkills().getFirst().id());
        org.junit.jupiter.api.Assertions.assertEquals("all", json.readTree(captured.getValue().getInput()).get("focus").asText());
    }

}
