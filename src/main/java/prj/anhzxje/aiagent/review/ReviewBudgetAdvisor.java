package prj.anhzxje.aiagent.review;

import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

/** Runs inside the tool loop, immediately before the terminal model advisor. */
public final class ReviewBudgetAdvisor implements CallAdvisor {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LlmBudgetService.Session budget;
    public ReviewBudgetAdvisor(LlmBudgetService.Session budget) { this.budget = budget; }
    @Override public String getName() { return "ReviewBudget"; }
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE - 1; }
    @Override public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        try {
            // Include assistant tool arguments and tool responses, not only plain user text.
            long chars = MAPPER.writeValueAsString(request.prompt().getInstructions()).length();
            if (request.prompt().getOptions() instanceof org.springframework.ai.model.tool.ToolCallingChatOptions options
                    && options.getToolCallbacks() != null) {
                for (var callback : options.getToolCallbacks()) {
                    var definition = callback.getToolDefinition();
                    chars += definition.name().length() + definition.description().length() + definition.inputSchema().length();
                }
            }
            budget.beforeModelCall(chars);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể đo context trước khi gọi model", e);
        }
        return chain.nextCall(request);
    }
}
