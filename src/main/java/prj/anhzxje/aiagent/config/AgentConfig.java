package prj.anhzxje.aiagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình hạ tầng cốt lõi cho AI Agent:
 * 1. ThreadPoolTaskExecutor: Quản lý luồng thực thi bất đồng bộ (@Async) cho các tác vụ AI phân tích mã nguồn.
 * 2. ObjectMapper: Định dạng JSON tuần hoàn chuẩn ISO cho thời gian (LocalDateTime) cho toàn bộ API.
 */
@Configuration
@EnableAsync
public class AgentConfig {

    /**
     * Thread Pool chuyên dụng cho việc thực thi tác vụ AI Agent.
     * Tác vụ phân tích mã nguồn tốn thời gian gọi LLM (từ 5 - 30 giây),
     * do đó việc tách riêng Pool luồng giúp ứng dụng Web chính không bao giờ bị nghẽn (non-blocking).
     */
    @Bean(name = "agentThreadPoolExecutor")
    public Executor agentThreadPoolExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // 5 luồng xử lý song song cơ bản
        executor.setMaxPoolSize(10); // Mở rộng tối đa 10 luồng khi có lượng tải tăng đột biến
        executor.setQueueCapacity(50); // Hàng đợi chờ tối đa 50 tác vụ
        executor.setThreadNamePrefix("AgentExecutor-");
        executor.initialize();
        return executor;
    }

    /**
     * ObjectMapper bean với JavaTimeModule — hỗ trợ serialize LocalDateTime đúng cách.
     * SecurityConfig cần bean này để trả JSON error response.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
