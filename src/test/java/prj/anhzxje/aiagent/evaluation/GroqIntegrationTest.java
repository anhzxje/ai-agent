package prj.anhzxje.aiagent.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

class GroqIntegrationTest {
    @Test void pilotIsPinnedAndPreviewIsOfflineAndBounded() throws Exception {
        var c=ExperimentConfiguration.load(Path.of("evaluation"),"configs/groq-pilot.json");
        assertEquals("groq",c.provider()); assertEquals("openai/gpt-oss-120b",c.model());
        var p=new EvaluationRunner(Path.of("evaluation"),Path.of("/tmp/unused-evaluation"),null)
            .preview(c,List.of(Group.R0,Group.R1,Group.R2),Set.of(),1);
        assertEquals(10,p.get("maximumModelDispatches")); assertEquals(0,p.get("networkRequestsInDryRun"));
        assertThrows(IllegalArgumentException.class,()->ExperimentConfiguration.requireRealApproval(c,"pilot",null,null));
    }
    @Test void quotaWaitsWhenMinuteWindowIsFullAndFailsClosedForOversizedRequest() throws Exception {
        var quota=new GroqQuota(java.time.Clock.systemUTC());
        var prompt=new org.springframework.ai.chat.prompt.Prompt("a");
        for(int i=0;i<5;i++) quota.reserve(prompt,1024);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var reservation=executor.submit(()->quota.reserve(prompt,1024));
            assertThrows(java.util.concurrent.TimeoutException.class,()->reservation.get(300,java.util.concurrent.TimeUnit.MILLISECONDS));
            reservation.cancel(true);
        } finally {executor.shutdownNow();}
        assertThrows(GroqQuota.Exhausted.class,()->new GroqQuota(java.time.Clock.systemUTC()).reserve(new org.springframework.ai.chat.prompt.Prompt("x".repeat(16000)),1024));
    }
    @Test void quotaResumesAfterOldReservationExpires() {
        class FakeClock extends java.time.Clock {
            long now=1_000_000;
            @Override public java.time.ZoneId getZone() {return java.time.ZoneOffset.UTC;}
            @Override public java.time.Clock withZone(java.time.ZoneId zone) {return this;}
            @Override public java.time.Instant instant() {return java.time.Instant.ofEpochMilli(now);}
            @Override public long millis() {return now;}
        }
        var clock=new FakeClock();var waits=new ArrayList<Long>();
        var quota=new GroqQuota(clock,millis->{waits.add(millis);clock.now+=millis;});
        var prompt=new org.springframework.ai.chat.prompt.Prompt("a");
        for(int i=0;i<6;i++) quota.reserve(prompt,1024);
        assertEquals(List.of(60_000L),waits);
        assertThrows(GroqQuota.Exhausted.class,()->quota.reserve(new org.springframework.ai.chat.prompt.Prompt("x".repeat(16000)),1024));
        assertEquals(List.of(60_000L),waits);
    }
    @Test void toolResultReachesGroqOptionsMockAndEvidenceIsValidated() throws Exception {
        var c=ExperimentConfiguration.load(Path.of("evaluation"),"configs/groq-pilot.json");
        var fixture=new EvaluationDataset(Path.of("evaluation")).load(c.dataset()).getFirst();
        var loc=fixture.truth().getFirst().locations().getFirst();
        var source=java.nio.file.Files.readAllLines(fixture.sourceRoot().resolve(loc.filePath()));
        int line=loc.lineStart(); String evidence=source.get(line-1).strip();
        assertFalse(evidence.isBlank());
        var model=org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class);
        org.mockito.Mockito.when(model.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());
        var call=new org.springframework.ai.chat.messages.AssistantMessage.ToolCall("read","function","file_system",
            EvaluationFiles.JSON.writeValueAsString(Map.of("path",loc.filePath(),"startLine",line,"endLine",line)));
        var first=new org.springframework.ai.chat.model.ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(
            org.springframework.ai.chat.messages.AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        var report=EvaluationFiles.JSON.createObjectNode().put("schemaVersion",2).put("summary","Mock review");
        report.putArray("suggestions");report.putArray("limitations");
        report.putArray("issues").addObject().put("classification","CONFIRMED_BUG").put("severity","HIGH").put("category","SECURITY")
            .put("filePath",loc.filePath()).put("lineStart",line).put("lineEnd",line).put("evidence",evidence)
            .put("explanation","Mock finding").put("suggestion","Inspect");
        var second=new org.springframework.ai.chat.model.ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage(report.toString()))));
        org.mockito.Mockito.when(model.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(first,second);
        var result=EvaluationGeneration.generate(c,fixture,Group.R1,c.limits(),model,"MOCK_INTEGRATION");
        assertEquals("COMPLETED",result.status());
        var captor=org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        org.mockito.Mockito.verify(model,org.mockito.Mockito.times(2)).call(captor.capture());
        assertTrue(captor.getAllValues().get(1).getInstructions().stream().anyMatch(m->m instanceof org.springframework.ai.chat.messages.ToolResponseMessage t && t.getResponses().stream().anyMatch(response->response.responseData().contains("SELECT id FROM accounts"))));
        var inspected=EvaluationValidation.inspect(result,fixture,true);
        assertTrue(inspected.outcome().schemaValid());assertTrue(inspected.outcome().evidenceValid());
        assertEquals(List.of(true),inspected.evidence());
    }
    @Test void openAiAdapterUsesMockEndpointAndDoesNotRetryFailure() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var requests=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/chat/completions",exchange->{
            requests.incrementAndGet();
            var body=exchange.getRequestBody().readAllBytes();
            assertTrue(new String(body,java.nio.charset.StandardCharsets.UTF_8).contains("openai/gpt-oss-120b"));
            byte[] response="{\"error\":{\"message\":\"quota\",\"type\":\"rate_limit_error\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(429,response.length);
            try(var out=exchange.getResponseBody()) {out.write(response);}
        });
        server.start();
        try {
            var model=EvaluationGeneration.groqModel("test-key","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
            var options=org.springframework.ai.openai.OpenAiChatOptions.builder().model("openai/gpt-oss-120b").build();
            assertThrows(Exception.class,()->model.call(new org.springframework.ai.chat.prompt.Prompt("test",options)));
            assertEquals(1,requests.get());
        } finally {server.stop(0);}
    }
    @Test void openAiAdapterCompletesToolLoopAgainstLocalMock() throws Exception {
        var c=ExperimentConfiguration.load(Path.of("evaluation"),"configs/groq-pilot.json");
        var fixture=new EvaluationDataset(Path.of("evaluation")).load(c.dataset()).getFirst();
        var loc=fixture.truth().getFirst().locations().getFirst();
        int line=loc.lineStart();String evidence=java.nio.file.Files.readAllLines(fixture.sourceRoot().resolve(loc.filePath())).get(line-1).strip();
        var report=EvaluationFiles.JSON.createObjectNode().put("schemaVersion",2).put("summary","Mock");
        report.putArray("suggestions");report.putArray("limitations");
        report.putArray("issues").addObject().put("classification","POTENTIAL_RISK").put("severity","HIGH").put("category","SECURITY")
            .put("filePath",loc.filePath()).put("lineStart",line).put("lineEnd",line).put("evidence",evidence).put("explanation","Mock").put("suggestion","Inspect");
        var requests=new java.util.concurrent.CopyOnWriteArrayList<String>();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            requests.add(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            var response=EvaluationFiles.JSON.createObjectNode().put("id","mock-id").put("object","chat.completion").put("created",1).put("model","openai/gpt-oss-120b");
            var choice=response.putArray("choices").addObject().put("index",0).put("finish_reason",requests.size()==1?"tool_calls":"stop");
            var message=choice.putObject("message").put("role","assistant");
            if(requests.size()==1) {
                var tool=message.putArray("tool_calls").addObject().put("id","call_1").put("type","function").putObject("function");
                tool.put("name","file_system").put("arguments",EvaluationFiles.JSON.writeValueAsString(Map.of("path",loc.filePath(),"startLine",line,"endLine",line)));
            } else message.put("content",report.toString());
            byte[] bytes=EvaluationFiles.JSON.writeValueAsBytes(response);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.start();
        try {
            var model=EvaluationGeneration.groqModel("test-key","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
            var generation=EvaluationGeneration.generate(c,fixture,Group.R1,c.limits(),model,"MOCK_INTEGRATION");
            assertEquals("COMPLETED",generation.status(),generation.errorCode());assertEquals(2,requests.size());assertEquals(2,generation.usage().modelDispatches());
            assertTrue(requests.get(1).contains("SELECT id FROM accounts"));
            var inspected=EvaluationValidation.inspect(generation,fixture,true);
            assertTrue(inspected.outcome().schemaValid());assertEquals(List.of(true),inspected.evidence());
        } finally {server.stop(0);}
    }
}
