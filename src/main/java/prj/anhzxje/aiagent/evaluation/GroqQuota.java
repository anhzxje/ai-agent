package prj.anhzxje.aiagent.evaluation;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayDeque;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/** In-process conservative reservation; not provider telemetry or account-wide coordination. */
final class GroqQuota {
    private record Reservation(long time, long tokens) {}
    private final ArrayDeque<Reservation> entries = new ArrayDeque<>();
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
    private final Clock clock;
    private final Sleeper sleeper;
    GroqQuota(Clock clock) { this(clock, Thread::sleep); }
    GroqQuota(Clock clock, Sleeper sleeper) { this.clock=clock; this.sleeper=sleeper; }
    synchronized void reserve(Prompt prompt, int maxOutput) {
        long bytes;
        try {
            bytes=EvaluationFiles.JSON.writeValueAsBytes(prompt.getInstructions()).length+1024L;
            if(prompt.getOptions() instanceof ToolCallingChatOptions options && options.getToolCallbacks()!=null)
                for(var callback:options.getToolCallbacks()) {
                    var d=callback.getToolDefinition();
                    bytes+=(d.name()+d.description()+d.inputSchema()).getBytes(StandardCharsets.UTF_8).length;
                }
        } catch(Exception e) { throw new Exhausted(); }
        long tokens=(bytes+1)/2+maxOutput;
        if(tokens>8000) throw new Exhausted();
        while(true) {
            long now=clock.millis();
            if(!entries.isEmpty() && entries.peekLast().time()>now) throw new Exhausted();
            while(!entries.isEmpty() && entries.peekFirst().time()<=now-86_400_000L) entries.removeFirst();
            long minuteTokens=0, dayTokens=0, firstMinute=Long.MAX_VALUE; int minuteCount=0;
            for(var entry:entries) {
                dayTokens+=entry.tokens();
                if(entry.time()>now-60_000L) {
                    minuteTokens+=entry.tokens();minuteCount++;
                    firstMinute=Math.min(firstMinute,entry.time());
                }
            }
            if(dayTokens+tokens>200000 || entries.size()>=1000) throw new Exhausted();
            if(minuteTokens+tokens<=8000 && minuteCount<30) {
                entries.addLast(new Reservation(now,tokens));return;
            }
            try { sleeper.sleep(firstMinute+60_000L-now); }
            catch(InterruptedException e) { Thread.currentThread().interrupt();throw new Exhausted(); }
        }
    }
    static final class Exhausted extends IllegalStateException {}
}
