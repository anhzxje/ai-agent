package prj.anhzxje.aiagent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetProperties;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.AgentToolConfig;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.AgentToolConfigRepository;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService;
import prj.anhzxje.aiagent.infrastructure.audit.AuditLogService;
import prj.anhzxje.aiagent.infrastructure.tools.ToolManagerService;
import prj.anhzxje.aiagent.skills.PromptComposer;
import prj.anhzxje.aiagent.skills.ReviewSkillContext;
import prj.anhzxje.aiagent.skills.SkillRegistry;
import prj.anhzxje.aiagent.skills.SkillResolver;
import prj.anhzxje.aiagent.skills.SkillSelection;
import prj.anhzxje.aiagent.skills.SkillValidationService;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises real ChatClient tool loop; only external model/DB configuration are mocked. */
class ReviewQualityIntegrationTest {
    @TempDir Path root;
    final ObjectMapper mapper = new ObjectMapper();

    ChatResponse response(String content) { return new ChatResponse(List.of(new Generation(new AssistantMessage(content)))); }
    ChatResponse readCall(int start, int end) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall("read", "function", "file_system",
                "{\"path\":\"AccountRepository.java\",\"startLine\":"+start+",\"endLine\":"+end+"}"))).build())));
    }
    String report(String evidence, int line) throws Exception {
        var output=mapper.createObjectNode().put("schemaVersion",2).put("summary","Kiểm tra truy vấn");
        output.putArray("suggestions"); output.putArray("limitations");
        var issues=output.putArray("issues");
        if(evidence!=null) issues.addObject().put("classification","CONFIRMED_BUG").put("severity","HIGH")
            .put("category","SECURITY").put("filePath","AccountRepository.java").put("lineStart",line).put("lineEnd",line)
            .put("evidence",evidence).put("explanation","Tên không tin cậy được nối trực tiếp vào SQL trước executeQuery.")
            .put("suggestion","Dùng PreparedStatement và bind tham số.");
        return mapper.writeValueAsString(output);
    }
    CodeReviewAgent agent(ChatModel model, LlmBudgetProperties limits) {
        var repository=mock(AgentToolConfigRepository.class);
        when(repository.findByEnabledTrue()).thenReturn(List.of(AgentToolConfig.builder().toolName("file_system").enabled(true).build()));
        var tools=new ToolManagerService(repository,mock(AuditLogService.class));
        ReflectionTestUtils.setField(tools,"allowedPaths",List.of(root.toString()));
        var budget=new LlmBudgetService(limits);
        return new CodeReviewAgent(ChatClient.builder(model),tools,null,new PromptComposer(budget),budget);
    }
    ChatModel model() {
        var model=mock(ChatModel.class);
        when(model.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        return model;
    }
    void fixture(String name) throws Exception {
        Files.copy(Path.of("src/test/resources/review-fixtures",name,"AccountRepository.java"),root.resolve("AccountRepository.java"));
    }
    @Test void securitySkillUsesReadEvidenceAndPersistsObservedScopeAndUsage() throws Exception {
        fixture("vulnerable");
        String evidence=Files.readAllLines(root.resolve("AccountRepository.java")).get(5).trim();
        var model=model();
        when(model.call(any(Prompt.class))).thenReturn(readCall(1,8),response(report(evidence,6)));
        var limits=new LlmBudgetProperties(); var budget=new LlmBudgetService(limits);
        var validation=new SkillValidationService();
        var registry=new SkillRegistry(mapper,new DefaultResourceLoader(),validation);
        var skills=new SkillResolver(registry,validation,budget).resolve(List.of(new SkillSelection("security-review",null)),"Java");
        var output=mapper.readTree(agent(model,limits).reviewPath(root.toString(),"security",List.of("file_system"),"gemini-3.5-flash-lite",
            new ReviewSkillContext(skills,"Java","Fixture")));
        assertEquals(evidence,output.at("/issues/0/evidence").asText());
        assertEquals(6,output.at("/issues/0/lineStart").asInt());
        assertEquals("AccountRepository.java",output.at("/issues/0/file").asText());
        assertEquals(2,output.at("/reviewMetadata/modelCalls").asInt());
        assertEquals(1,output.at("/reviewMetadata/toolCalls").asInt());
        assertEquals("AccountRepository.java",output.at("/reviewMetadata/filesRead/0/filePath").asText());
        var capture=org.mockito.ArgumentCaptor.forClass(Prompt.class); verify(model,times(2)).call(capture.capture());
        var first=capture.getAllValues().getFirst();
        assertTrue(first.getSystemMessage().getText().contains("lineStart"));
        assertTrue(first.getUserMessage().getText().contains("security-review@1.0.0"));
        assertFalse(first.getUserMessage().getText().contains("performance-review@"));
        assertEquals("gemini-3.5-flash-lite",first.getOptions().getModel());
    }
    @Test void cleanParameterizedFixtureMayReturnZeroIssuesWithoutInventingFindings() throws Exception {
        fixture("clean"); var model=model();
        when(model.call(any(Prompt.class))).thenReturn(readCall(1,10),response(report(null,0)));
        var output=mapper.readTree(agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
        assertTrue(output.path("issues").isEmpty()); assertFalse(output.path("limitations").isEmpty());
        assertEquals(1,output.at("/reviewMetadata/filesRead").size());
        verify(model,times(2)).call(any(Prompt.class));
    }
    @Test void retainsGroundedFindingsWhenAnotherIssueHasUnverifiedEvidence() throws Exception {
        fixture("vulnerable");
        String evidence=Files.readAllLines(root.resolve("AccountRepository.java")).get(5).trim();
        var report=(com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(report(evidence,6));
        var invalid=((com.fasterxml.jackson.databind.node.ObjectNode) report.at("/issues/0")).deepCopy();
        invalid.put("evidence","fabricated code");
        report.withArray("issues").add(invalid);
        var model=model();
        when(model.call(any(Prompt.class))).thenReturn(readCall(1,8),response(mapper.writeValueAsString(report)));
        var output=mapper.readTree(agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
        assertEquals(1,output.path("issues").size());
        assertEquals(evidence,output.at("/issues/0/evidence").asText());
        assertTrue(output.path("limitations").toString().contains("bằng chứng"));
        verify(model,times(2)).call(any(Prompt.class));
    }
    @Test void rejectsFabricatedEvidenceOrUnreadLinesWithoutModelRepairCall() throws Exception {
        fixture("vulnerable");
        for (int line : List.of(6,99)) {
            var model=model(); when(model.call(any(Prompt.class))).thenReturn(readCall(1,8),response(report("invented call",line)));
            assertThrows(IllegalStateException.class,() -> agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
            verify(model,times(2)).call(any(Prompt.class));
        }
    }
    @Test void noReadHasExplicitLimitationAndCannotClaimEvidencedIssue() throws Exception {
        fixture("vulnerable"); var model=model();
        when(model.call(any(Prompt.class))).thenReturn(response(report(null,0)));
        var output=mapper.readTree(agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
        assertTrue(output.path("limitations").toString().contains("Chưa đọc"));
        assertTrue(output.at("/reviewMetadata/filesRead").isEmpty());
        when(model.call(any(Prompt.class))).thenReturn(response(report("executeQuery",6)));
        assertThrows(IllegalStateException.class,() -> agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
    }
    @Test void rejectsRealQuoteFromUnreadRangeOrAttributedToWrongLine() throws Exception {
        fixture("vulnerable");
        String evidence=Files.readAllLines(root.resolve("AccountRepository.java")).get(5).trim();
        for(int citedLine:List.of(6,7)) {
            var model=model();
            when(model.call(any(Prompt.class))).thenReturn(readCall(1,citedLine==6 ? 5 : 8),response(report(evidence,citedLine)));
            assertThrows(IllegalStateException.class,() -> agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
            verify(model,times(2)).call(any(Prompt.class));
        }
    }
    @Test void safeInProjectSymlinkUsesThePathActuallyRequestedByTheModel() throws Exception {
        fixture("vulnerable");
        String evidence=Files.readAllLines(root.resolve("AccountRepository.java")).get(5).trim();
        Files.move(root.resolve("AccountRepository.java"),root.resolve("Original.java"));
        try { Files.createSymbolicLink(root.resolve("AccountRepository.java"),Path.of("Original.java")); }
        catch(java.io.IOException | UnsupportedOperationException error) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,"OS does not allow symlinks");
        }
        var model=model(); when(model.call(any(Prompt.class))).thenReturn(readCall(1,8),response(report(evidence,6)));
        var output=mapper.readTree(agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
        assertEquals("AccountRepository.java",output.at("/reviewMetadata/filesRead/0/filePath").asText());
    }
    @Test void budgetFailureIsInternalAndNeverReportedAsGeminiOrRetried() throws Exception {
        fixture("vulnerable");
        var model=model(); when(model.call(any(Prompt.class))).thenReturn(readCall(1,8));
        var limits=new LlmBudgetProperties(); limits.setMaxModelCalls(1);
        var error=assertThrows(LlmBudgetService.BudgetExceededException.class,() -> agent(model,limits).reviewPath(root.toString(),"security"));
        assertEquals("MODEL_CALLS",error.reason()); assertFalse(error.getMessage().contains("Lỗi từ Gemini"));
        verify(model,times(1)).call(any(Prompt.class));
    }
    @Test void contextLimitErrorsAreNotMisreportedAsProviderQuota() throws Exception {
        fixture("vulnerable"); var model=model();
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("context length limit exceeded"));
        var error=assertThrows(RuntimeException.class, () -> agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
        assertFalse(error.getMessage().contains("Rate limit/Quota"));
        verify(model,times(1)).call(any(Prompt.class));
    }
    @Test void provider429IsReportedAsQuotaWithoutRetry() throws Exception {
        fixture("vulnerable"); var model=model();
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED"));
        var error=assertThrows(RuntimeException.class, () -> agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security"));
        assertTrue(error.getMessage().contains("Rate limit/Quota"));
        verify(model,times(1)).call(any(Prompt.class));
    }
    @Test void cancellationStopsFurtherToolAndModelCalls() throws Exception {
        fixture("vulnerable"); var model=model();
        var active = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> { active.set(false); return readCall(1,8); });
        assertThrows(java.util.concurrent.CancellationException.class, () ->
            agent(model,new LlmBudgetProperties()).reviewPath(root.toString(),"security",List.of("file_system"),
                "gemini-3.5-flash-lite",ReviewSkillContext.legacy(), active::get));
        verify(model,times(1)).call(any(Prompt.class));
    }
}
