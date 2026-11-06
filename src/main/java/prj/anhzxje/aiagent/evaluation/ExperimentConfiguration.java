package prj.anhzxje.aiagent.evaluation;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationDataset.require;

public final class ExperimentConfiguration {
    private ExperimentConfiguration() {}
    public static Config load(Path root,String file) throws IOException {
        var c=EvaluationFiles.JSON.readValue(EvaluationFiles.read(EvaluationFiles.inside(root,file)),Config.class);
        return validate(c);
    }
    public static Config validate(Config c) {
        require(c.schemaVersion()==1 && c.mode()!=null && c.dataset()!=null,"Experiment config schema");
        require(c.model()!=null && (("google-genai".equals(c.provider()) && Set.of("gemini-3.5-flash-lite","gemini-2.5-flash").contains(c.model()))
            || ("groq".equals(c.provider()) && "openai/gpt-oss-120b".equals(c.model()))),"Provider/model ID");
        require(c.modelOptions()!=null && c.modelOptions().keySet().equals(Set.of("temperature"))
            && c.modelOptions().get("temperature") instanceof Number n && n.doubleValue()>=0 && n.doubleValue()<=2,"Explicit supported model options");
        require(c.tools()!=null && !c.tools().isEmpty() && new HashSet<>(c.tools()).size()==c.tools().size()
            && Set.of("file_system","list_directory","glob","grep").containsAll(c.tools()),"Read-only tools");
        require(c.skills()!=null && new HashSet<>(c.skills()).size()==c.skills().size()
            && c.skills().stream().allMatch(s->s.id()!=null && s.version()!=null),"Pin every unique skill version");
        var l=c.limits(); require(l!=null,"Missing budgets");
        require(l.maxCases()>0 && l.maxCases()<=100 && l.maxModelCalls()>0 && l.maxModelCalls()<=25
            && l.maxToolCalls()>0 && l.maxToolCalls()<=24 && l.maxContextChars()>0 && l.maxContextChars()<=96000
            && l.maxPromptChars()>0 && l.maxPromptChars()<=24000 && l.maxSkillChars()>0 && l.maxSkillChars()<=8000
            && l.maxSkills()>0 && l.maxSkills()<=3 && l.maxOutputTokens()>0 && l.maxOutputTokens()<=4096
            && l.maxRequestsPerCase()>0 && l.maxRequestsPerCase()<=25 && l.maxTotalRequests()>0 && l.maxTotalRequests()<=2500
            && l.maxTotalToolCalls()>0 && l.maxTotalToolCalls()<=2400 && l.concurrency()==1 && l.retries()==0,"Unsafe/unsupported budgets");
        if ("groq".equals(c.provider())) require(l.maxCases()==1 && l.maxModelCalls()<=5 && l.maxRequestsPerCase()<=5
            && l.maxTotalRequests()<=10 && l.maxOutputTokens()<=1024,"Groq pilot limits");
        if(c.mode()==Mode.OFFLINE_REPLAY) require(c.replays()!=null && !c.replays().isEmpty(),"Replay map required");
        var limits=new prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetProperties();
        limits.setMaxSkills(l.maxSkills());limits.setMaxSkillChars(l.maxSkillChars());
        var validation=new prj.anhzxje.aiagent.skills.SkillValidationService();
        var registry=new prj.anhzxje.aiagent.skills.SkillRegistry(EvaluationFiles.JSON,new org.springframework.core.io.DefaultResourceLoader(),validation);
        new prj.anhzxje.aiagent.skills.SkillResolver(registry,validation,new prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService(limits)).resolve(c.skills(),"Java");
        if(c.pricing()!=null) {
            var p=c.pricing();
            require("USD".equals(p.currency()) && Double.isFinite(p.inputUsdPerMillionTokens()) && p.inputUsdPerMillionTokens()>=0
                && Double.isFinite(p.outputUsdPerMillionTokens()) && p.outputUsdPerMillionTokens()>=0
                && p.source()!=null && p.source().startsWith("https://") && p.effectiveDate()!=null,"Explicit valid unit pricing required");
            java.time.LocalDate.parse(p.effectiveDate()); // Never fetch a pricing URL automatically.
        }
        return c;
    }
    /** A flag alone never constructs a provider. Human approval binds exact config, ID and expiry. */
    public static void requireRealApproval(Config config,String id,String confirmation,Path approval) throws IOException {
        if(config.mode()!=Mode.REAL_LLM) return;
        String hash=EvaluationFiles.hash(config);
        require(confirmation!=null && confirmation.equals("APPROVE_REAL_LLM:"+id+":"+hash),"Explicit real experiment confirmation required");
        require(approval!=null && Files.isRegularFile(approval),"Approval document required");
        var document=EvaluationFiles.JSON.readTree(EvaluationFiles.read(approval));
        require(document.path("approved").asBoolean(false) && id.equals(document.path("experimentId").asText())
            && hash.equals(document.path("configHash").asText()) && !document.path("approvedBy").asText().isBlank()
            && Instant.parse(document.path("expiresAt").asText()).isAfter(Instant.now()),"Approval mismatch/expired");
    }
}
