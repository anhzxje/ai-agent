package prj.anhzxje.aiagent.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

class EvaluationIntegrationTest {
    @TempDir Path temp;
    final Path root=Path.of("evaluation");
    Config config(Mode mode,boolean store) throws Exception {
        var c=ExperimentConfiguration.load(root,"configs/offline-development.json");
        return new Config(c.schemaVersion(),c.dataset(),mode,c.provider(),c.model(),c.modelOptions(),c.tools(),c.skills(),c.limits(),c.replays(),null,store,c.pricing());
    }
    @Test void actualChatClientPipelineUsesSkillsToolsAndBoundedContextWithoutTruthLeakage() throws Exception {
        var c=config(Mode.MOCK_INTEGRATION,true); var fixture=new EvaluationDataset(root).load(c.dataset()).getFirst();
        var model=mock(ChatModel.class); when(model.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        String path=fixture.truth().getFirst().locations().getFirst().filePath();
        var call=new AssistantMessage.ToolCall("read","function","file_system",EvaluationFiles.JSON.writeValueAsString(Map.of("path",path,"startLine",1,"endLine",100)));
        var read=new ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        var done=new ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(new AssistantMessage("{\"schemaVersion\":2,\"summary\":\"Mock\",\"issues\":[],\"suggestions\":[],\"limitations\":[]}"))));
        when(model.call(any(Prompt.class))).thenReturn(read,done,read,done);
        var r0=EvaluationGeneration.generate(c,fixture,Group.R0,c.limits(),model,"MOCK_INTEGRATION");
        var r1=EvaluationGeneration.generate(c,fixture,Group.R1,c.limits(),model,"MOCK_INTEGRATION");
        assertEquals(2,r0.usage().modelDispatches());assertEquals(1,r1.usage().toolInvocations());
        assertEquals(path,r1.observations().getFirst().filePath());
        assertNull(r1.usage().inputTokens());
        var prompts=org.mockito.ArgumentCaptor.forClass(Prompt.class);verify(model,times(4)).call(prompts.capture());
        String baseline=prompts.getAllValues().get(0).getContents(),skill=prompts.getAllValues().get(2).getContents();
        assertFalse(baseline.contains("security-review@"));assertTrue(skill.contains("security-review@1.0.0"));
        assertTrue(baseline.contains("\"language\":\"Java\",\"name\":\"inventory-api\""));
        assertTrue(skill.contains("\"language\":\"Java\",\"name\":\"inventory-api\""));
        for(var truth:fixture.truth()) {assertFalse(skill.contains(truth.defectId()));assertFalse(skill.contains(truth.rootCause()));}
        assertEquals(c.model(),prompts.getAllValues().getFirst().getOptions().getModel());
    }
    @Test void r2ReusesR1GenerationAndStrictlyRejectsFabricatedEvidence() throws Exception {
        var c=config(Mode.MOCK_INTEGRATION,true); AtomicInteger generated=new AtomicInteger();
        var runner=new EvaluationRunner(root,temp,(fixture,group,limits)-> {
            generated.incrementAndGet(); var loc=fixture.truth().getFirst().locations().getFirst();
            var report=EvaluationFiles.JSON.createObjectNode().put("schemaVersion",2).put("summary","Mock with unsupported evidence");
            report.putArray("suggestions");report.putArray("limitations");
            report.putArray("issues").addObject().put("classification","CONFIRMED_BUG").put("severity","HIGH").put("category","SECURITY")
                .put("filePath",loc.filePath()).put("lineStart",loc.lineStart()).put("lineEnd",loc.lineEnd()).put("evidence","invented evidence")
                .put("explanation","Unknown cause").put("suggestion","Inspect source");
            return new Generation("COMPLETED",report.toString(),List.of(),null,null,null,"MOCK_INTEGRATION");
        });
        var rows=runner.run(c,"validation",List.of(Group.R1,Group.R2),Set.of("inventory-api"),1,false,null,null);
        assertEquals(1,generated.get());assertEquals(rows.get(0).generationHash(),rows.get(1).generationHash());
        assertEquals(1,rows.get(0).acceptedFindingCount());assertEquals(0,rows.get(1).acceptedFindingCount());
        assertEquals(1,rows.get(1).rejectedFindingCount());assertEquals(1,rows.get(1).rawFindingCount());
        assertEquals("INVALID_REPORT",rows.get(1).status());assertFalse(rows.get(1).validation().evidenceValid());
        assertEquals(10,rows.get(1).acceptedScores().overall().fn());
    }
    @Test void malformedAndLegacyReportsAreRetainedWithoutRepairCalls() throws Exception {
        var c=config(Mode.MOCK_INTEGRATION,true);
        for(String raw:List.of("{broken","{\"summary\":\"Legacy\",\"issues\":[],\"suggestions\":[]}")) {
            AtomicInteger calls=new AtomicInteger();
            var runner=new EvaluationRunner(root,temp,(f,g,l)-> {calls.incrementAndGet();return new Generation("COMPLETED",raw,List.of(),null,null,null,"MOCK_INTEGRATION");});
            String id=raw.startsWith("{broken")?"malformed":"legacy";
            var rows=runner.run(c,id,List.of(Group.R1,Group.R2),Set.of("inventory-api"),1,false,null,null);
            assertEquals(1,calls.get());assertEquals("INVALID_REPORT",rows.get(1).status());
            if(id.equals("legacy")) assertTrue(rows.get(0).validation().schemaValid());
            assertEquals(10,rows.get(1).acceptedScores().overall().fn());
        }
    }
    @Test void wholeReportRejectionCanLoseTruePositivesAndMustRemainVisible() throws Exception {
        var c=config(Mode.MOCK_INTEGRATION,true);
        var synthetic=EvaluationFiles.JSON.readValue(Path.of("evaluation/replays/inventory-api-R1.json").toFile(),Generation.class);
        var report=EvaluationFiles.JSON.readTree(synthetic.rawOutput());
        ((com.fasterxml.jackson.databind.node.ObjectNode)report.at("/issues/2")).put("evidence","not-in-source");
        var runner=new EvaluationRunner(root,temp,(f,g,l)->new Generation("COMPLETED",report.toString(),synthetic.observations(),null,null,null,"MOCK_INTEGRATION"));
        var rows=runner.run(c,"mixed",List.of(Group.R1,Group.R2),Set.of("inventory-api"),1,false,null,null);
        assertEquals(2,rows.get(0).rawScores().overall().tp());
        assertEquals(2,rows.get(1).rawScores().overall().tp());
        assertEquals(0,rows.get(1).acceptedScores().overall().tp());
        assertEquals(3,rows.get(1).rejectedFindingCount());
        assertNull(rows.get(1).acceptedScores().overall().precision());
    }
    @Test void realApprovalIsBoundToConfigIdAndExpiryWithoutConstructingAProvider() throws Exception {
        var real=config(Mode.REAL_LLM,false);Path approval=temp.resolve("approval.json");String hash=EvaluationFiles.hash(real);
        EvaluationFiles.create(approval,Map.of("approved",true,"approvedBy","test-operator","experimentId","real-test","configHash",hash,"expiresAt","2099-01-01T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,()->ExperimentConfiguration.requireRealApproval(real,"real-test","--real-llm",approval));
        assertDoesNotThrow(()->ExperimentConfiguration.requireRealApproval(real,"real-test","APPROVE_REAL_LLM:real-test:"+hash,approval));
        assertThrows(IllegalArgumentException.class,()->ExperimentConfiguration.requireRealApproval(real,"another-id","APPROVE_REAL_LLM:real-test:"+hash,approval));
    }
    @Test void csvEscapesCellsAndSecretPatternsAreRedacted() {
        assertEquals("\"a,b\"",EvaluationExporter.cell("a,b"));
        assertEquals("\"' =SUM(1,2)\"",EvaluationExporter.cell(" =SUM(1,2)"));
        assertEquals("\"a\"\"b\"",EvaluationExporter.cell("a\"b"));
        String fake="AIza"+"X".repeat(32);
        assertFalse(EvaluationFiles.redact(fake+" Bearer fake-token password=fake-value").contains(fake));
        assertFalse(EvaluationFiles.redact("password=fake-value").contains("fake-value"));
    }
    @Test void costRequiresKnownProviderTokensAndExplicitPrices() {
        var prices=new Pricing("USD",1,2,"https://example.test/fixture-pricing","2026-11-06");
        assertEquals(1.2,EvaluationExporter.estimateCost(List.of(new Usage(2,1,100L,100L,1_000_000L,100_000L)),prices));
        assertNull(EvaluationExporter.estimateCost(List.of(new Usage(2,1,100L,100L,null,null)),prices));
        assertNull(EvaluationExporter.estimateCost(List.of(new Usage(2,1,100L,100L,10L,10L)),null));
    }
    @Test void actualPipelineStopsAtModelBudgetAndQuotaWithoutRetry() throws Exception {
        var c=config(Mode.MOCK_INTEGRATION,true);var fixture=new EvaluationDataset(root).load(c.dataset()).getFirst();
        var model=mock(ChatModel.class);when(model.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("429 RESOURCE_EXHAUSTED"));
        var quota=EvaluationGeneration.generate(c,fixture,Group.R1,c.limits(),model,"MOCK_INTEGRATION");
        assertEquals("QUOTA_EXHAUSTED",quota.status());verify(model,times(1)).call(any(Prompt.class));
        var l=c.limits();var bounded=new Limits(1,1,1,1,l.maxToolCalls(),l.maxTotalToolCalls(),l.maxPromptChars(),l.maxContextChars(),l.maxSkillChars(),l.maxSkills(),1,0,l.maxOutputTokens());
        var generator=EvaluationGeneration.generator(new Config(1,c.dataset(),Mode.MOCK_INTEGRATION,c.provider(),c.model(),c.modelOptions(),c.tools(),c.skills(),bounded,c.replays(),null,true,null));
        var budget=generator.generate(fixture,Group.R1,bounded);
        assertEquals("BUDGET_EXHAUSTED",budget.status());assertEquals(1,budget.usage().modelDispatches());
    }
    @Test void withholdingRawFindingsNeverRegeneratesCompletedCasesOnResume() throws Exception {
        var c=config(Mode.MOCK_INTEGRATION,false); AtomicInteger calls=new AtomicInteger();
        var runner=new EvaluationRunner(root,temp,(f,g,l)-> {calls.incrementAndGet();return new Generation("COMPLETED","{\"schemaVersion\":2,\"summary\":\"UNSTORED_CONTENT\",\"issues\":[],\"suggestions\":[],\"limitations\":[]}",List.of(),null,null,null,"MOCK_INTEGRATION");});
        runner.run(c,"privacy",List.of(Group.R1,Group.R2),Set.of("inventory-api"),1,false,null,null);
        try(var files=Files.list(temp.resolve("privacy"))) {for(Path p:files.filter(Files::isRegularFile).toList()) assertFalse(Files.readString(p).contains("UNSTORED_CONTENT"));}
        Files.delete(temp.resolve("privacy/inventory-api-R2.case.json"));
        var rows=runner.run(c,"privacy",List.of(Group.R1,Group.R2),Set.of("inventory-api"),1,true,null,null);
        assertEquals(1,calls.get());assertEquals("RAW_NOT_STORED_RESUME",rows.get(1).errorCode());
    }
}
