package prj.anhzxje.aiagent.infrastructure.ai;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class LlmBudgetService {
    private final LlmBudgetProperties limits;
    public LlmBudgetService(LlmBudgetProperties limits) { this.limits = limits; }

    public String reviewGuidance() {
        return "Application review limits: max model calls=" + limits.getMaxModelCalls()
                + ", max tool calls=" + limits.getMaxToolCalls() + ", max context characters=" + limits.getMaxContextChars()
                + ". Prioritize relevant evidence, reserve room for the final report, stop exploration early and disclose unreviewed scope.";
    }

    public static final class BudgetExceededException extends IllegalStateException {
        private final String reason;
        private BudgetExceededException(String reason, long used, long maximum) {
            super("Đã hết budget nội bộ của phiên review: " + reason + " (" + used + "/" + maximum
                    + "). Thu hẹp phạm vi review; đây không phải quota của provider.");
            this.reason = reason;
        }
        public String reason() { return reason; }
    }

    public record ReadScope(String filePath, int lineStart, int lineEnd) {}
    public record Usage(int modelCalls, int toolCalls, long peakRequestChars, long accumulatedContextChars,
                        int maxModelCalls, int maxToolCalls, long maxContextChars, List<ReadScope> filesRead) {}

    public void validateSkills(int count, long chars) {
        if (limits.getMaxSkills() < 1 || limits.getMaxSkillChars() < 1
                || count > limits.getMaxSkills() || chars > limits.getMaxSkillChars()) {
            throw new IllegalArgumentException("Skill vượt budget: số lượng hoặc tổng ký tự");
        }
    }
    public void validatePrompt(String policy, String prompt) {
        if (limits.getMaxPromptChars() < 1 || (long) policy.length() + prompt.length() > limits.getMaxPromptChars()) {
            throw new IllegalArgumentException("Prompt vượt agent.llm-budget.max-prompt-chars");
        }
    }
    public Session openSession(String policy, String prompt) {
        return openSession(policy, prompt, () -> true);
    }
    public Session openSession(String policy, String prompt, java.util.function.BooleanSupplier active) {
        validatePrompt(policy, prompt);
        if (limits.getMaxToolCalls() < 1 || limits.getMaxModelCalls() < 1 || limits.getMaxContextChars() < 1
                || (long) policy.length() + prompt.length() > limits.getMaxContextChars()) {
            throw new IllegalArgumentException("Context budget không hợp lệ hoặc đã vượt giới hạn");
        }
        return new Session(limits.getMaxToolCalls(), limits.getMaxModelCalls(), limits.getMaxContextChars(), (long) policy.length() + prompt.length(), java.util.Objects.requireNonNull(active));
    }
    /** Shared by all tools in one review; no model calls or selection calls here. */
    public static final class Session {
        private final int maxCalls;
        private final long maxChars;
        private final int maxModelCalls;
        private final java.util.function.BooleanSupplier active;
        private int modelCalls;
        private int calls;
        private long chars;
        private BudgetExceededException exhaustion;
        private long peakRequestChars;
        private final Map<String, NavigableMap<Integer,String>> reads = new TreeMap<>();
        private Session(int maxCalls, int maxModelCalls, long maxChars, long initialChars, java.util.function.BooleanSupplier active) {
            this.maxCalls = maxCalls; this.maxModelCalls = maxModelCalls; this.maxChars = maxChars; this.chars = initialChars; this.active = active;
        }
        public synchronized void beforeModelCall(long requestChars) {
            requireAvailable();
            if (modelCalls >= maxModelCalls) fail("MODEL_CALLS",modelCalls+1L,maxModelCalls);
            if (requestChars > maxChars) fail("REQUEST_CONTEXT",requestChars,maxChars);
            modelCalls++;
            peakRequestChars=Math.max(peakRequestChars,requestChars);
        }
        public synchronized void beforeToolCall() {
            requireAvailable();
            if (calls >= maxCalls) fail("TOOL_CALLS",calls+1L,maxCalls);
            calls++;
        }
        public synchronized String recordToolOutput(String output) {
            requireAvailable();
            if (chars + output.length() > maxChars) fail("TOOL_CONTEXT",chars+output.length(),maxChars);
            chars += output.length();
            return output;
        }
        public synchronized void requireAvailable() {
            if (exhaustion != null) throw exhaustion;
            if (!active.getAsBoolean()) throw new java.util.concurrent.CancellationException("Tác vụ review đã bị hủy hoặc thay thế");
        }
        private void fail(String reason,long used,long maximum) {
            exhaustion=new BudgetExceededException(reason,used,maximum);
            throw exhaustion;
        }
        public synchronized String remainingGuidance() {
            return "\n[Application budget remaining: " + (maxCalls-calls) + " tool calls, " + (maxModelCalls-modelCalls)
                + " model calls. Return findings and limitations before exhaustion.]";
        }
        /** Record only source lines actually delivered by file_system, never a full transcript. */
        public synchronized void recordRead(String path,Map<Integer,String> lines) {
            reads.computeIfAbsent(path,key->new TreeMap<>()).putAll(lines);
        }
        public synchronized boolean hasEvidence(String path,int start,int end,String evidence) {
            var lines=reads.get(path);
            if(lines==null) return false;
            var excerpt=new StringBuilder();
            for(long line=start;line<=end;line++) {
                if(!lines.containsKey((int)line)) return false;
                excerpt.append(lines.get((int)line)).append('\n');
            }
            return excerpt.toString().contains(evidence.replace("\r\n","\n").strip());
        }
        public synchronized Usage usage() {
            List<ReadScope> scopes=new ArrayList<>();
            reads.forEach((path,lines)-> {
                int start=-1,previous=-1;
                for(int line:lines.keySet()) {
                    if(start<0) start=line;
                    else if(line!=previous+1) { scopes.add(new ReadScope(path,start,previous)); start=line; }
                    previous=line;
                }
                if(start>=0) scopes.add(new ReadScope(path,start,previous));
            });
            return new Usage(modelCalls,calls,peakRequestChars,chars,maxModelCalls,maxCalls,maxChars,List.copyOf(scopes));
        }
    }
}
