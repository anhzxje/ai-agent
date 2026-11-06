package prj.anhzxje.aiagent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import prj.anhzxje.aiagent.infrastructure.ai.SystemConfigService;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.*;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.*;
import prj.anhzxje.aiagent.infrastructure.tools.ToolManagerService;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.shared.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.tasks.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ChatReviewPersistenceTest.Config.class)
class ChatReviewPersistenceTest {
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses = AgentTaskRepository.class)
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:chat-review-integration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(User.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean prj.anhzxje.aiagent.skills.SkillTaskSupport skillSupport() { return mock(prj.anhzxje.aiagent.skills.SkillTaskSupport.class); }
        @Bean prj.anhzxje.aiagent.infrastructure.configuration.AgentProperties agentProperties() { return new prj.anhzxje.aiagent.infrastructure.configuration.AgentProperties(); }
        @Bean AgentTaskExecutor executor() { return mock(AgentTaskExecutor.class); }
        @Bean SystemConfigService config() { var c = mock(SystemConfigService.class); when(c.getAllowedModels()).thenReturn(java.util.List.of("allowed")); return c; }
        @Bean ToolManagerService tools() { return mock(ToolManagerService.class); }
        @Bean AgentTaskService taskService(AgentTaskRepository tasks, ProjectRepository projects, AgentTaskExecutor executor) {
            return new AgentTaskService(tasks, projects, executor);
        }
        @Bean ChatReviewService reviews(ChatConversationRepository conversations, ChatReviewLinkRepository links,
                AgentTaskRepository tasks, AgentTaskService service, SystemConfigService config, ToolManagerService tools, ObjectMapper mapper) {
            return new ChatReviewService(conversations, links, tasks, service, config, tools, mapper);
        }
    }
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ChatConversationRepository conversations;
    @Autowired ChatReviewLinkRepository links;
    @Autowired AgentTaskRepository tasks;
    @Autowired ChatReviewService reviews;
    @Autowired AgentTaskExecutor executor;
    @Autowired DataSource dataSource;

    @Test void concurrentSubmissionsPersistOneLinkAndDispatchAfterCommit() throws Exception {
        String name = UUID.randomUUID().toString();
        User owner = users.save(User.builder().username(name).email(name + "@test.invalid").password("hash").build());
        User other = users.save(User.builder().username("other-" + name).email("other-" + name + "@test.invalid").password("hash").build());
        Project project = projects.save(Project.builder().name("test").path("/test").user(owner).build());
        ChatConversation conversation = conversations.save(ChatConversation.builder().title("test").user(owner).project(project).model("allowed").build());
        var request = new ChatReviewRequest(); request.setMessage("/review security"); request.setRequestId("same-request");
        var observedCommitted = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            try (var c = dataSource.getConnection(); var ps = c.prepareStatement("select count(*) from chat_review_links where task_id = ?")) {
                ps.setLong(1, invocation.getArgument(0));
                try (var rs = ps.executeQuery()) { rs.next(); observedCommitted.set(rs.getInt(1) == 1); }
            }
            return null;
        }).when(executor).executeTaskAsync(anyLong(), anyString(), anyString(), any());
        CountDownLatch start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            Callable<Long> submit = () -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(owner), null));
                try { start.await(); return reviews.submit(conversation.getId(), request, owner).getId(); }
                finally { SecurityContextHolder.clearContext(); }
            };
            Future<Long> first = threads.submit(submit), second = threads.submit(submit);
            start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, links.findByConversationIdOrderByIdAsc(conversation.getId()).size());
        verify(executor, times(1)).executeTaskAsync(anyLong(), anyString(), anyString(), any());
        assertTrue(observedCommitted.get(), "task must be dispatched after the link commits");
        assertThrows(ResourceNotFoundException.class, () -> reviews.list(conversation.getId(), other));
        Long taskId = links.findByConversationIdOrderByIdAsc(conversation.getId()).getFirst().getTask().getId();
        assertEquals(1, tasks.claimTask(taskId, "execution-1"));
        assertTrue(tasks.existsByIdAndStatusAndExecutionId(taskId, prj.anhzxje.aiagent.shared.enums.JobStatus.PROCESSING, "execution-1"));
        assertEquals(1, tasks.cancelActiveTask(taskId, java.time.LocalDateTime.now()));
        assertFalse(tasks.existsByIdAndStatusAndExecutionId(taskId, prj.anhzxje.aiagent.shared.enums.JobStatus.PROCESSING, "execution-1"));
        assertEquals(0, tasks.finishTask(taskId, "execution-1", prj.anhzxje.aiagent.shared.enums.JobStatus.COMPLETED, null, null, java.time.LocalDateTime.now()));
        conversations.deleteById(conversation.getId());
        assertTrue(links.findByConversationIdOrderByIdAsc(conversation.getId()).isEmpty());
        assertEquals(1, tasks.count(), "deleting a chat must preserve the review task");
    }
}
