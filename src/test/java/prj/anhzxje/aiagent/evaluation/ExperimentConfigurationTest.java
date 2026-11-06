package prj.anhzxje.aiagent.evaluation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentConfigurationTest {
    @TempDir Path temp;
    ObjectNode config() throws Exception {
        return (ObjectNode)EvaluationFiles.JSON.readTree(Path.of("evaluation/configs/offline-development.json").toFile());
    }
    void refuses(ObjectNode config) throws Exception {
        EvaluationFiles.JSON.writeValue(temp.resolve("config.json").toFile(),config);
        assertThrows(IllegalArgumentException.class,()->ExperimentConfiguration.load(temp,"config.json"));
    }
    @Test void missingSkillOrVersionFailsBeforeAnyGeneration() throws Exception {
        var missing=config();((ObjectNode)missing.at("/skills/0")).put("id","not-installed");refuses(missing);
        var version=config();((ObjectNode)version.at("/skills/0")).put("version","9.0.0");refuses(version);
    }
    @Test void retriesConcurrencyAndOversizedBudgetsAreRejected() throws Exception {
        for(var entry:java.util.Map.of("retries",1,"concurrency",2,"maxModelCalls",26,"maxContextChars",96001,"maxTotalRequests",0).entrySet()) {
            var config=config();((ObjectNode)config.path("limits")).put(entry.getKey(),entry.getValue());refuses(config);
        }
        var options=config();((ObjectNode)options.path("modelOptions")).put("apiKey","fictional-value");refuses(options);
    }
}
