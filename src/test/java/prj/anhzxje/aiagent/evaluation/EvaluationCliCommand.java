package prj.anhzxje.aiagent.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit Maven CLI launcher; name intentionally excludes normal *Test discovery. No Spring context. */
class EvaluationCliCommand {
    @Test void command() throws Exception {
        String file=System.getProperty("evaluation.argsFile");
        if(file==null) throw new IllegalArgumentException("Use scripts/evaluate.sh");
        assertEquals(0,EvaluationCli.execute(Files.readAllLines(Path.of(file)).toArray(String[]::new)),"Evaluation command failed safely");
    }
}
