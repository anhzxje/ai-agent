package prj.anhzxje.aiagent.infrastructure.ai;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoRetryAiConfigurationTest {
    @Test void springAndGoogleLayersDoNotRetryQuotaErrors() {
        var calls = new AtomicInteger();
        var retry = new NoRetryAiConfiguration().aiRetryTemplate();
        assertThrows(RuntimeException.class, () -> retry.invoke(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("429 quota exhausted");
        }));
        assertEquals(1, calls.get());
        assertEquals(1, NoRetryAiConfiguration.noRetryHttpOptions().retryOptions().orElseThrow().attempts().orElseThrow());
        assertEquals(java.util.Optional.of(60000), NoRetryAiConfiguration.noRetryHttpOptions().timeout());
    }
}
