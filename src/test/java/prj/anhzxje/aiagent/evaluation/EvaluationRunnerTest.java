package prj.anhzxje.aiagent.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

class EvaluationRunnerTest {
    @TempDir Path results;
    final Path root=Path.of("evaluation");
    @Test void corruptedCheckpointCannotSilentlyChangeResearchResultsOnResume() throws Exception {
        var config=ExperimentConfiguration.load(root,"configs/offline-development.json");
        var runner=new EvaluationRunner(root,results,null);
        runner.run(config,"integrity",List.of(Group.R0),Set.of("inventory-api"),1,false,null,null);
        var file=results.resolve("integrity/inventory-api-R0.case.json");
        var document=EvaluationFiles.JSON.readTree(file.toFile());
        var data=document.has("data")?document.path("data"):document;
        ((com.fasterxml.jackson.databind.node.ObjectNode)data).put("status","CORRUPTED");
        EvaluationFiles.JSON.writeValue(file.toFile(),document);
        assertThrows(IllegalArgumentException.class,()->runner.run(config,"integrity",List.of(Group.R0),Set.of("inventory-api"),1,true,null,null));
    }
    @Test void replayResumeExportsAreOfflineAndRefuseOverwrite() throws Exception {
        var config=ExperimentConfiguration.load(root,"configs/offline-development.json");
        AtomicInteger calls=new AtomicInteger();
        EvaluationRunner.Generator forbidden=(c,g,l)-> {calls.incrementAndGet();throw new AssertionError("Offline must never generate");};
        var runner=new EvaluationRunner(root,results,forbidden);
        var report=runner.run(config,"offline-test",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,false,null,null);
        assertEquals(6,report.size()); assertEquals(0,calls.get());
        assertTrue(report.stream().allMatch(r->r.provenance().equals("SYNTHETIC_FIXTURE")));
        assertTrue(Files.readString(results.resolve("offline-test/summary.csv")).contains("SYNTHETIC_FIXTURE"));
        assertTrue(Files.readString(results.resolve("offline-test/research-report.md")).contains("No actual LLM quality results"));
        assertThrows(FileAlreadyExistsException.class,()->runner.run(config,"offline-test",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,false,null,null));
        var resumed=runner.run(config,"offline-test",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,true,null,null);
        assertEquals(report,resumed); assertEquals(0,calls.get());
        assertThrows(IllegalArgumentException.class,()->runner.run(config,"offline-test",List.of(Group.R0),Set.of(),2,true,null,null));
    }
    @Test void realModeRequiresMoreThanAnOptionAndDryRunDoesNotGenerate() throws Exception {
        var config=ExperimentConfiguration.load(root,"configs/offline-development.json");
        var real=new Config(1,config.dataset(),Mode.REAL_LLM,config.provider(),config.model(),config.modelOptions(),config.tools(),config.skills(),config.limits(),config.replays(),null,false,config.pricing());
        AtomicInteger calls=new AtomicInteger();
        var runner=new EvaluationRunner(root,results,(c,g,l)-> {calls.incrementAndGet(); throw new AssertionError();});
        assertThrows(IllegalArgumentException.class,()->runner.run(real,"blocked",List.of(Group.R1),Set.of(),1,false,null,null));
        var preview=runner.preview(real,List.of(Group.R0,Group.R1,Group.R2),Set.of(),2);
        assertEquals(100,preview.get("maximumModelDispatches")); assertEquals(0,calls.get());
        assertFalse(Files.exists(results.resolve("blocked")));
    }
    @Test void quotaOrBudgetStopsWithoutRetryAndErrorsRemainInScores() throws Exception {
        var config=ExperimentConfiguration.load(root,"configs/offline-development.json");
        var mock=new Config(1,config.dataset(),Mode.MOCK_INTEGRATION,config.provider(),config.model(),config.modelOptions(),config.tools(),config.skills(),config.limits(),config.replays(),null,true,config.pricing());
        for(String failure:List.of("QUOTA_EXHAUSTED","BUDGET_EXHAUSTED")) {
            AtomicInteger calls=new AtomicInteger();
            var runner=new EvaluationRunner(root,results,(c,g,l)-> {
                calls.incrementAndGet(); return new Generation(failure,null,List.of(),new Usage(1,0,10L,10L,null,null),1L,failure,"MOCK_INTEGRATION");
            });
            var outcomes=runner.run(mock,failure,List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,false,null,null);
            assertEquals(1,calls.get()); assertEquals(6,outcomes.size());
            assertTrue(outcomes.stream().allMatch(r->r.acceptedScores().overall().tp()==0));
            assertTrue(outcomes.stream().anyMatch(r->r.acceptedScores().overall().fn()>0));
            runner.run(mock,failure,List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,true,null,null);
            assertEquals(1,calls.get());
        }
    }
    @Test void comparisonExportsTablesButRejectsDifferentModels() throws Exception {
        var c=ExperimentConfiguration.load(root,"configs/offline-development.json");
        var runner=new EvaluationRunner(root,results,null);
        runner.run(c,"a",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,false,null,null);
        runner.run(c,"b",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,false,null,null);
        new ExperimentComparison().compare(results,List.of("a","b"),results.resolve("comparison"));
        assertTrue(Files.readString(results.resolve("comparison/comparison.csv")).contains("SYNTHETIC_FIXTURE"));
        var changed=new Config(c.schemaVersion(),c.dataset(),c.mode(),c.provider(),"gemini-2.5-flash",c.modelOptions(),c.tools(),c.skills(),c.limits(),c.replays(),null,c.storeRawFindings(),null);
        runner.run(changed,"c",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,false,null,null);
        assertThrows(IllegalArgumentException.class,()->new ExperimentComparison().compare(results,List.of("a","c"),results.resolve("unequal")));
    }
    @Test void identicalReportsFromDifferentPhysicalUnitsHaveDistinctGenerationIdentity() throws Exception {
        var c=ExperimentConfiguration.load(root,"configs/offline-development.json");
        var mock=new Config(c.schemaVersion(),c.dataset(),Mode.MOCK_INTEGRATION,c.provider(),c.model(),c.modelOptions(),c.tools(),c.skills(),c.limits(),c.replays(),null,true,null);
        var runner=new EvaluationRunner(root,results,(f,g,l)->new Generation("COMPLETED","{\"schemaVersion\":2,\"summary\":\"Same\",\"issues\":[],\"suggestions\":[],\"limitations\":[]}",List.of(),null,null,null,"MOCK_INTEGRATION"));
        var rows=runner.run(mock,"identity",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,false,null,null);
        assertNotEquals(rows.get(0).generationHash(),rows.get(1).generationHash());
        assertEquals(rows.get(1).generationHash(),rows.get(2).generationHash());
        assertNotEquals(rows.get(1).generationHash(),rows.get(4).generationHash());
    }
    @Test void interruptedGenerationIsNotAutomaticallyRetriedOnResume() throws Exception {
        var c=ExperimentConfiguration.load(root,"configs/offline-development.json");
        var mock=new Config(c.schemaVersion(),c.dataset(),Mode.MOCK_INTEGRATION,c.provider(),c.model(),c.modelOptions(),c.tools(),c.skills(),c.limits(),c.replays(),null,true,null);
        AtomicInteger calls=new AtomicInteger();
        var runner=new EvaluationRunner(root,results,(f,g,l)->{calls.incrementAndGet();throw new AssertionError("Simulated process interruption before checkpoint");});
        assertThrows(AssertionError.class,()->runner.run(mock,"interrupted",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,false,null,null));
        var resumed=runner.run(mock,"interrupted",List.of(Group.R0,Group.R1,Group.R2),Set.of(),2,true,null,null);
        assertEquals(1,calls.get());assertEquals(6,resumed.size());
        assertEquals("INTERRUPTED_GENERATION_NOT_RETRIED",resumed.getFirst().errorCode());
    }
}
