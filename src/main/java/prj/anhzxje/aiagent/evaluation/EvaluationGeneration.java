package prj.anhzxje.aiagent.evaluation;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.*;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.core.io.*;
import org.springframework.core.retry.RetryTemplate;
import prj.anhzxje.aiagent.infrastructure.tools.SandboxTools;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetProperties;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService;
import prj.anhzxje.aiagent.review.ReviewBudgetAdvisor;
import prj.anhzxje.aiagent.skills.PromptComposer;
import prj.anhzxje.aiagent.skills.SkillDefinition;
import prj.anhzxje.aiagent.skills.SkillRegistry;
import prj.anhzxje.aiagent.skills.SkillResolver;
import prj.anhzxje.aiagent.skills.SkillValidationService;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

/** Research generation only: intentionally exposes raw output before result validation. */
final class EvaluationGeneration {
    static EvaluationRunner.Generator generator(Config config) {
        if(config.mode()==Mode.OFFLINE_REPLAY) throw new IllegalArgumentException("Offline has no generation adapter");
        var quota=new GroqQuota(java.time.Clock.systemUTC());
        return (fixture,group,limits)-> {
            if(config.mode()==Mode.MOCK_INTEGRATION) return generate(config,fixture,group,limits,mock(config,fixture),"MOCK_INTEGRATION");
            // Reached only after runner's exact-config approval gate. Never load .env automatically.
            if ("groq".equals(config.provider())) {
                String key=System.getenv("GROQ_API_KEY");
                if(key==null || key.isBlank()) throw new IllegalArgumentException("GROQ_API_KEY required");
                var delegate=groqModel(key,"https://api.groq.com/openai/v1");
                ChatModel guarded=new ChatModel() {
                    @Override public ChatResponse call(Prompt p) {quota.reserve(p,limits.maxOutputTokens());return delegate.call(p);}
                    @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {return delegate.getOptions();}
                };
                return generate(config,fixture,group,limits,guarded,"ACTUAL_LLM");
            }
            String key=System.getenv("GOOGLE_API_KEY");
            if(key==null || key.isBlank()) throw new IllegalArgumentException("GOOGLE_API_KEY must be explicitly provided for approved real run");
            try(var client=Client.builder().apiKey(key).vertexAI(false).httpOptions(HttpOptions.builder()
                    .timeout(60000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build()) {
                var retry=new RetryTemplate(org.springframework.core.retry.RetryPolicy.withMaxRetries(0));
                var model=new GoogleGenAiChatModel(client,GoogleGenAiChatOptions.builder().build(),ToolCallingManager.builder().build(),retry,ObservationRegistry.NOOP);
                return generate(config,fixture,group,limits,model,"ACTUAL_LLM");
            }
        };
    }
    static ChatModel groqModel(String key,String baseUrl) {
        var defaults=org.springframework.ai.openai.OpenAiChatOptions.builder().baseUrl(baseUrl).apiKey(key).maxRetries(0)
            .timeout(java.time.Duration.ofSeconds(60)).build();
        return org.springframework.ai.openai.OpenAiChatModel.builder().options(defaults).build();
    }
    static Generation generate(Config config,LoadedCase fixture,Group group,Limits limits,ChatModel model,String provenance) throws Exception {
        long started=System.nanoTime();
        var properties=new LlmBudgetProperties();
        properties.setMaxModelCalls(Math.min(limits.maxModelCalls(),limits.maxRequestsPerCase())); properties.setMaxToolCalls(limits.maxToolCalls());
        properties.setMaxPromptChars(limits.maxPromptChars());properties.setMaxContextChars(limits.maxContextChars());
        properties.setMaxSkills(limits.maxSkills());properties.setMaxSkillChars(limits.maxSkillChars());
        var budgetService=new LlmBudgetService(properties);
        var validation=new SkillValidationService();
        var registry=new SkillRegistry(EvaluationFiles.JSON,new DefaultResourceLoader(),validation);
        var skills=group==Group.R0?List.<SkillDefinition>of():new SkillResolver(registry,validation,budgetService).resolve(config.skills(),"Java");
        String system=new ClassPathResource("prompts/code-reviewer.md").getContentAsString(StandardCharsets.UTF_8)+PromptComposer.SYSTEM_POLICY;
        String user=new PromptComposer(budgetService).compose("Review the Java project at sandbox root: "+fixture.sourceRoot()
            +"\nReview security, logic, performance and resource management. Read actual source and counterevidence. Follow the system report schema.\n"
            +budgetService.reviewGuidance(),skills,"Java",fixture.fixture().fixtureId());
        if(group==Group.R0) {
            // The production composer appends metadata only with skills. Keep source context equal in this ablation.
            user+="\nProject context (untrusted JSON data):\n"+EvaluationFiles.JSON.createObjectNode()
                .put("language","Java").put("name",fixture.fixture().fixtureId())+"\n";
        }
        var session=budgetService.openSession(system,user);
        var tokens=new TokenCounter();
        ChatModel observed=new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) {var response=model.call(prompt);tokens.add(response);return response;}
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {return model.getOptions();}
        };
        var sandbox=new SandboxTools(fixture.sourceRoot(),session);
        var tools=config.tools().stream().sorted().map(sandbox::tool).toArray();
        var safety=List.of(GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,
            GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_HARASSMENT,GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_HATE_SPEECH,
            GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT).stream().map(category->new GoogleGenAiSafetySetting(category,
                GoogleGenAiSafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH,GoogleGenAiSafetySetting.HarmBlockMethod.PROBABILITY)).toList();
        try {
            org.springframework.ai.chat.prompt.ChatOptions.Builder<?> options = "groq".equals(config.provider())
                ? org.springframework.ai.openai.OpenAiChatOptions.builder().model(config.model())
                    .temperature(((Number)config.modelOptions().get("temperature")).doubleValue()).maxCompletionTokens(limits.maxOutputTokens())
                : GoogleGenAiChatOptions.builder().model(config.model())
                    .temperature(((Number)config.modelOptions().get("temperature")).doubleValue()).maxOutputTokens(limits.maxOutputTokens()).safetySettings(safety);
            String output=ChatClient.builder(observed).build().prompt().system(system).user(user).tools(tools)
                .advisors(new ReviewBudgetAdvisor(session)).options(options).call().content();
            session.requireAvailable();
            return result("COMPLETED",output,null,provenance,session,tokens,started);
        } catch(Exception error) {
            try {session.requireAvailable();}
            catch(LlmBudgetService.BudgetExceededException exhausted) {return result("BUDGET_EXHAUSTED",null,exhausted.reason(),provenance,session,tokens,started);}
            String code="PROVIDER_OR_TOOL_ERROR";String status="ERROR";
            for(Throwable cause=error;cause!=null;cause=cause.getCause()) {
                if(cause instanceof GroqQuota.Exhausted) return result("BUDGET_EXHAUSTED",null,"GROQ_ESTIMATED_QUOTA",provenance,session,tokens,started);
                String message=cause.getMessage()==null?"":cause.getMessage();
                if(message.contains("429") || message.contains("RESOURCE_EXHAUSTED") || message.toLowerCase(Locale.ROOT).contains("quota")) {status="QUOTA_EXHAUSTED";code="PROVIDER_QUOTA_OR_RATE_LIMIT";break;}
                if(cause==cause.getCause()) break;
            }
            return result(status,null,code,provenance,session,tokens,started);
        }
    }
    private static Generation result(String status,String output,String code,String provenance,LlmBudgetService.Session session,TokenCounter tokens,long started) {
        var usage=session.usage();
        return new Generation(status,output,usage.filesRead(),new Usage(usage.modelCalls(),usage.toolCalls(),usage.peakRequestChars(),usage.accumulatedContextChars(),tokens.input(),tokens.output()),
            (System.nanoTime()-started)/1_000_000,code,provenance);
    }
    private static ChatModel mock(Config c,LoadedCase fixture) throws Exception {
        String file;
        try(var files=Files.walk(fixture.sourceRoot())) {file=fixture.sourceRoot().relativize(files.filter(p->p.toString().endsWith(".java")).sorted().findFirst().orElseThrow()).toString().replace('\\','/');}
        String tool=c.tools().contains("file_system")?"file_system":c.tools().getFirst();
        String args=switch(tool) {
            case "file_system"->EvaluationFiles.JSON.writeValueAsString(Map.of("path",file,"startLine",1,"endLine",100));
            case "list_directory"->"{\"path\":\".\"}";
            case "glob"->"{\"pattern\":\"**/*.java\"}";
            default->"{\"query\":\"class\"}";
        };
        AtomicInteger dispatches=new AtomicInteger();
        return new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return GoogleGenAiChatOptions.builder().build();
            }
            @Override public ChatResponse call(Prompt prompt) {
                AssistantMessage message;
                if(dispatches.getAndIncrement()==0) {
                    message=AssistantMessage.builder().content("").toolCalls(List.of(
                        new AssistantMessage.ToolCall("fixture-read","function",tool,args))).build();
                } else {
                    message=new AssistantMessage("{\"schemaVersion\":2,\"summary\":\"Mock integration only\",\"issues\":[],\"suggestions\":[],\"limitations\":[\"Mock model; no accuracy claim\"]}");
                }
                return new ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(message)));
            }
        };
    }
    private static final class TokenCounter {
        private long input,output; private boolean complete=true,seen;
        synchronized void add(ChatResponse response) {
            var usage=response==null?null:response.getMetadata().getUsage();
            if(usage==null || usage instanceof org.springframework.ai.chat.metadata.EmptyUsage
                    || usage.getPromptTokens()==null || usage.getCompletionTokens()==null) {complete=false;return;}
            seen=true;input+=usage.getPromptTokens();output+=usage.getCompletionTokens();
        }
        synchronized Long input() {return complete && seen?input:null;}
        synchronized Long output() {return complete && seen?output:null;}
    }
}
