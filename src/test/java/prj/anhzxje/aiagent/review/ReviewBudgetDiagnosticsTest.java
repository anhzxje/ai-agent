package prj.anhzxje.aiagent.review;

import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService;

import org.junit.jupiter.api.Test;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetProperties;
import static org.junit.jupiter.api.Assertions.*;

class ReviewBudgetDiagnosticsTest {
    @Test void concurrentToolAccountingNeverPermitsMoreThanTheSharedLimit() throws Exception {
        var limits=new LlmBudgetProperties(); limits.setMaxToolCalls(31);
        var session=new LlmBudgetService(limits).openSession("p","q");
        try(var threads=java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var work=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<100;i++) work.add(threads.submit(() -> {
                try { session.beforeToolCall(); return true; }
                catch(LlmBudgetService.BudgetExceededException e) { return false; }
            }));
            int accepted=0; for(var result:work) if(result.get()) accepted++;
            assertEquals(31,accepted); assertEquals(31,session.usage().toolCalls());
        }
    }
    @Test void preservesFirstExhaustionReasonAndCountsOnlyPermittedDispatches() {
        var limits = new LlmBudgetProperties(); limits.setMaxModelCalls(1);
        var session = new LlmBudgetService(limits).openSession("p", "q");
        session.beforeModelCall(20);
        var failure=assertThrows(LlmBudgetService.BudgetExceededException.class, () -> session.beforeModelCall(30));
        assertEquals("MODEL_CALLS",failure.reason());
        assertEquals(1,session.usage().modelCalls());
        assertSame(failure,assertThrows(LlmBudgetService.BudgetExceededException.class,session::requireAvailable));
        assertSame(failure,assertThrows(LlmBudgetService.BudgetExceededException.class,session::beforeToolCall));
    }
    @Test void distinguishesSerializedRequestFromAccumulatedToolContext() {
        var limits=new LlmBudgetProperties(); limits.setMaxContextChars(100);
        var service=new LlmBudgetService(limits);
        var request=service.openSession("p","q");
        assertEquals("REQUEST_CONTEXT",assertThrows(LlmBudgetService.BudgetExceededException.class, () -> request.beforeModelCall(101)).reason());
        var output=service.openSession("p","q");
        assertEquals("TOOL_CONTEXT",assertThrows(LlmBudgetService.BudgetExceededException.class, () -> output.recordToolOutput("x".repeat(99))).reason());
    }
}
