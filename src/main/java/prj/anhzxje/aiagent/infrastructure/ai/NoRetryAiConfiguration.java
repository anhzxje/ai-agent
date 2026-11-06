package prj.anhzxje.aiagent.infrastructure.ai;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/** Disable retries at both the Spring AI and Google SDK layers. */
@Configuration
public class NoRetryAiConfiguration {
    @Bean
    @ConditionalOnMissingBean(RetryTemplate.class)
    RetryTemplate aiRetryTemplate() { return new RetryTemplate(RetryPolicy.withMaxRetries(0)); }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Client.class)
    @ConditionalOnProperty(name = "spring.ai.google.genai.api-key")
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        return Client.builder().apiKey(apiKey).httpOptions(noRetryHttpOptions()).build();
    }

    static HttpOptions noRetryHttpOptions() {
        return HttpOptions.builder().timeout(60000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build();
    }
}
