package prj.anhzxje.aiagent.repository;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import prj.anhzxje.aiagent.entity.*;
import prj.anhzxje.aiagent.enums.*;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(TaskLifecycleIntegrationTest.PersistenceConfig.class)
class TaskLifecycleIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AgentTaskRepository.class)
    static class PersistenceConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:review-tests;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("prj.anhzxje.aiagent.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @Autowired AgentTaskRepository tasks;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ChatConversationRepository conversations;
    @Autowired ChatMessageRepository messages;
    private Long taskId;
    private User user;

    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        user = users.save(User.builder().username(suffix).email(suffix + "@example.test").password("hash").build());
        var project = projects.save(Project.builder().user(user).name("project").path("/workspace").build());
        taskId = tasks.save(AgentTask.builder().user(user).project(project).type(TaskType.CODE_REVIEW).input("{}").build()).getId();
    }

    @Test void onlyOneConcurrentWorkerCanClaimTask() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> { start.await(); return tasks.claimTask(taskId, "first"); });
            Future<Integer> second = executor.submit(() -> { start.await(); return tasks.claimTask(taskId, "second"); });
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(JobStatus.PROCESSING, tasks.findById(taskId).orElseThrow().getStatus());
    }

    @Test void staleWorkerCannotOverwriteCancelledOrRetriedTask() {
        assertEquals(1, tasks.claimTask(taskId, "old"));
        assertEquals(0, tasks.resetForRetry(taskId));
        assertEquals(1, tasks.cancelActiveTask(taskId, LocalDateTime.now()));
        assertEquals(0, tasks.finishTask(taskId, "old", JobStatus.COMPLETED, "{}", null, LocalDateTime.now()));
        assertEquals(JobStatus.CANCELLED, tasks.findById(taskId).orElseThrow().getStatus());
        assertEquals(1, tasks.resetForRetry(taskId));
        assertEquals(1, tasks.claimTask(taskId, "new"));
        assertEquals(0, tasks.finishTask(taskId, "old", JobStatus.ERROR, null, "late error", LocalDateTime.now()));
        assertEquals(1, tasks.finishTask(taskId, "new", JobStatus.COMPLETED, "{}", null, LocalDateTime.now()));
        var task = tasks.findById(taskId).orElseThrow();
        assertEquals(JobStatus.COMPLETED, task.getStatus());
        assertEquals("{}", task.getOutput());
        assertNull(task.getErrorMessage());
        assertEquals(0, tasks.cancelActiveTask(taskId, LocalDateTime.now()));
    }

    @Test void rejectedAndDeletedTasksDoNotRemainRunnable() {
        assertEquals(1, tasks.rejectPendingTask(taskId, "queue full", LocalDateTime.now()));
        assertEquals(JobStatus.ERROR, tasks.findById(taskId).orElseThrow().getStatus());
        assertEquals(0, tasks.claimTask(taskId, "worker"));
        tasks.deleteById(taskId);
        assertEquals(0, tasks.claimTask(taskId, "worker"));
    }

    @Test void historyQueryReturnsOnlyTenLatestMessagesWithStableOrdering() {
        var conversation = conversations.save(ChatConversation.builder().user(user).title("test").build());
        LocalDateTime timestamp = LocalDateTime.now();
        for (int i = 0; i < 12; i++) {
            messages.save(ChatMessage.builder().conversation(conversation).sender("USER").content("message " + i).createdAt(timestamp).build());
        }
        var latest = messages.findTop10ByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId());
        assertEquals(10, latest.size());
        assertEquals("message 11", latest.getFirst().getContent());
        assertEquals("message 2", latest.getLast().getContent());
    }
}
