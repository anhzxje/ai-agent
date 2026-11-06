package prj.anhzxje.aiagent.review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                .thenReturn("```json\n{\"schemaVersion\":2,\"summary\":\"OK\",\"issues\":[],\"suggestions\":[],\"limitations\":[]}\n```");

        String resultSecurity = agent.reviewPath("/workspace/project", "security");
        assertTrue(resultSecurity.contains("\"summary\":\"OK\""));

        String resultPerformance = agent.reviewPath("/workspace/project", "performance");
        assertTrue(resultPerformance.contains("\"summary\":\"OK\""));

        String resultBug = agent.reviewPath("/workspace/project", "bug");
        assertTrue(resultBug.contains("\"summary\":\"OK\""));

        String resultCodeQuality = agent.reviewPath("/workspace/project", "code_quality");
        assertTrue(resultCodeQuality.contains("\"summary\":\"OK\""));

        String resultOther = agent.reviewPath("/workspace/project", "custom_focus");
        assertTrue(resultOther.contains("\"summary\":\"OK\""));

        String resultNull = agent.reviewPath("/workspace/project", null);
        assertTrue(resultNull.contains("\"summary\":\"OK\""));
    }

    @Test
    void securityFocusRetainsSpecializedGuidanceAlongsideUserDetail() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.defaultOptions(any())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        var agent = new CodeReviewAgent(chatClientBuilder);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("{\"schemaVersion\":2,\"summary\":\"OK\",\"issues\":[],\"suggestions\":[],\"limitations\":[]}");
        agent.reviewPath("/workspace/project", "security\nauth details");
        org.mockito.Mockito.verify(chatClient.prompt()).user(org.mockito.ArgumentMatchers.<String>argThat(text ->
                text.contains("authentication flow") && text.contains("auth details")));
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
