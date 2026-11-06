package prj.anhzxje.aiagent.chat;

import org.junit.jupiter.api.Test;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatMessage;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.Project;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatPresentationTest {
    private final Project project = Project.builder().name("App").path("/workspace/app").language("Java").description("API").build();

    @Test void keepsLegacySlashCommandTitlesAndMarkdownCleanup() {
        assertEquals("Hỏi đáp chung", ChatPresentation.conversationTitle(null, null));
        assertEquals("Hội thoại: App", ChatPresentation.conversationTitle(" ", project));
        assertEquals("Code Review: App", ChatPresentation.conversationTitle("/review", project));
        assertEquals("Review: Security", ChatPresentation.conversationTitle("/review security", project));
        assertEquals("Sửa lỗi: App", ChatPresentation.conversationTitle("/fix file: A.java", project));
        assertEquals("Giải thích: App", ChatPresentation.conversationTitle("/explain", project));
        assertEquals("Unit Test: App", ChatPresentation.conversationTitle("/test", project));
        assertEquals("Hello world", ChatPresentation.conversationTitle("## hello\n**world**", project));
        assertEquals("Hội thoại", ChatPresentation.conversationTitle("###", project));
    }

    @Test void historyKeepsLastTenMessagesInOrderAndDoesNotMutateCallerList() {
        var messages = new ArrayList<ChatMessage>();
        for (int i = 0; i < 11; i++) messages.add(ChatMessage.builder().sender(i % 2 == 0 ? "USER" : "AGENT").content("m" + i).build());
        String prompt = ChatPresentation.promptWithHistory(messages, "current");
        assertFalse(prompt.contains(": m0\n"));
        assertTrue(prompt.contains("Trợ lý AI: m1\n\nNgười dùng: m2"));
        assertTrue(prompt.endsWith("---\nCâu hỏi/Yêu cầu hiện tại của người dùng:\ncurrent"));
        assertEquals(11, messages.size());
        assertEquals("m0", messages.getFirst().getContent());
        assertEquals("current", ChatPresentation.promptWithHistory(List.of(), "current"));
    }

    @Test void keepsGeneralPromptAndProvidesProjectContextWithoutSimulatingReviewRouting() throws Exception {
        // General chat stays compatible; project chat no longer claims to execute slash commands.
        assertEquals("c9db50fcc9182758d868c105d98f2d2b28c7d0b43d8c6a7c190780522daae84a", hash(ChatPresentation.systemPrompt(null)));
        String prompt = ChatPresentation.systemPrompt(project);
        assertTrue(prompt.contains("/workspace/app"));
        assertTrue(prompt.contains("Java"));
        assertFalse(prompt.contains("/review"));
    }

    private static String hash(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
