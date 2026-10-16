package prj.anhzxje.aiagent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử Đơn vị cho AI Code Review Agent (CodeReviewAgentTest):
 * Sử dụng Mockito để giả lập ChatClient và kiểm thử:
 * 1. Các trọng tâm đánh giá khác nhau (security, performance, bug, code_quality, all).
 * 2. Khả năng bóc tách, làm sạch định dạng Markdown JSON ("```json ... ```").
 * 3. Xử lý trường hợp phản hồi rỗng từ LLM.
 */
@ExtendWith(MockitoExtension.class)
class CodeReviewAgentTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Test
    void testReviewPath_WithDifferentFocusTypes() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.defaultOptions(any())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        CodeReviewAgent agent = new CodeReviewAgent(chatClientBuilder);

        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("```json\n{\"summary\":\"OK\"}\n```");

        String resultSecurity = agent.reviewPath("/workspace/project", "security");
        assertEquals("{\"summary\":\"OK\"}", resultSecurity);

        String resultPerformance = agent.reviewPath("/workspace/project", "performance");
        assertEquals("{\"summary\":\"OK\"}", resultPerformance);

        String resultBug = agent.reviewPath("/workspace/project", "bug");
        assertEquals("{\"summary\":\"OK\"}", resultBug);

        String resultCodeQuality = agent.reviewPath("/workspace/project", "code_quality");
        assertEquals("{\"summary\":\"OK\"}", resultCodeQuality);

        String resultOther = agent.reviewPath("/workspace/project", "custom_focus");
        assertEquals("{\"summary\":\"OK\"}", resultOther);

        String resultNull = agent.reviewPath("/workspace/project", null);
        assertEquals("{\"summary\":\"OK\"}", resultNull);
    }

    @Test
    void testCleanJsonResponse() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.defaultOptions(any())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        CodeReviewAgent agent = new CodeReviewAgent(chatClientBuilder);

        assertNull(ReflectionTestUtils.invokeMethod(agent, "cleanJsonResponse", (String) null));
        assertEquals("{\"a\":1}", ReflectionTestUtils.invokeMethod(agent, "cleanJsonResponse", "```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", ReflectionTestUtils.invokeMethod(agent, "cleanJsonResponse", "Dưới đây là kết quả:\n```json\n{\"a\":1}\n```\nChúc bạn thành công!"));
        assertEquals("{\"a\":1}", ReflectionTestUtils.invokeMethod(agent, "cleanJsonResponse", "Here is JSON: {\"a\":1} hope it helps"));
        assertEquals("{\"a\":1}", ReflectionTestUtils.invokeMethod(agent, "cleanJsonResponse", "{\"a\":1}"));
    }
}
