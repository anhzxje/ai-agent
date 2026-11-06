package prj.anhzxje.aiagent.evaluation;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationDataset.require;

public final class EvaluationRunner {
    @FunctionalInterface public interface Generator {Generation generate(LoadedCase fixture,Group group,Limits limits) throws Exception;}
    private final Path root,results;
    private final Generator suppliedGenerator;
    public EvaluationRunner(Path root,Path results,Generator generator) {this.root=root;this.results=results;this.suppliedGenerator=generator;}

    public Map<String,Object> preview(Config c,List<Group> groups,Set<String> subset,int maximum) throws Exception {
        ExperimentConfiguration.validate(c);
        var selected=select(c,subset,maximum); var physical=groups.stream().filter(g->g!=Group.R2).count();
        if(groups.contains(Group.R2) && !groups.contains(Group.R1)) physical++;
        return Map.of("mode",c.mode(),"providerModel",Map.of("provider",c.provider(),"model",c.model()),"fixtureIds",selected.stream().map(f->f.fixture().fixtureId()).toList(),"groups",groups,
            "configHash",EvaluationFiles.hash(c),"maximumModelDispatches",(int)Math.min(c.limits().maxTotalRequests(),physical*selected.size()*Math.min(c.limits().maxModelCalls(),c.limits().maxRequestsPerCase())),
            "maximumToolInvocations",(int)Math.min(c.limits().maxTotalToolCalls(),physical*selected.size()*c.limits().maxToolCalls()),
            "concurrency",1,"networkRequestsInDryRun",0,"generationReuse","R2 reuses R1; no additional model dispatch");
    }
    private List<LoadedCase> select(Config c,Set<String> subset,int maximum) throws IOException {
        require(maximum>0 && maximum<=c.limits().maxCases(),"Maximum cases exceeds configured bound");
        var dataset=new EvaluationDataset(root); dataset.checkSplits("datasets/development.json","datasets/test.json");
        var cases=dataset.load(c.dataset());
        var ids=cases.stream().map(f->f.fixture().fixtureId()).toList(); require(ids.containsAll(subset),"Unknown subset");
        return cases.stream().filter(f->subset.isEmpty() || subset.contains(f.fixture().fixtureId())).limit(maximum).toList();
    }
    public List<CaseResult> run(Config c,String id,List<Group> requested,Set<String> subset,int maximum,
                                boolean resume,String confirmation,Path approval) throws Exception {
        require(id!=null && id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"),"Safe experiment ID required");
        ExperimentConfiguration.validate(c);
        var groups=requested.stream().distinct().sorted().toList(); require(!groups.isEmpty(),"Select a group");
        var cases=select(c,subset,maximum);
        ExperimentConfiguration.requireRealApproval(c,id,confirmation,approval);
        List<Adjudication> decisions=c.adjudications()==null ? List.of() : List.of(EvaluationFiles.JSON.readValue(
            EvaluationFiles.read(EvaluationFiles.inside(root,c.adjudications())),Adjudication[].class));
        require(decisions.stream().noneMatch(d->d.group()==Group.R2),"Annotate R1 generation once; R2 reuses the same semantic decisions");
        var signature=new LinkedHashMap<String,Object>(); signature.put("config",c); signature.put("groups",groups);
        signature.put("fixtures",cases.stream().map(LoadedCase::fixture).toList());
        signature.put("truthHash",EvaluationFiles.hash(cases.stream().map(LoadedCase::truth).toList()));
        signature.put("adjudications",decisions); signature.put("frameworkHash",frameworkHash());
        signature.put("replayHashes",replayHashes(c,cases,groups));
        byte[] datasetBytes=EvaluationFiles.read(EvaluationFiles.inside(root,c.dataset()));
        var datasetDocument=EvaluationFiles.JSON.readTree(datasetBytes);
        signature.put("datasetHash",EvaluationFiles.sha(datasetBytes));
        signature.put("datasetVersion",datasetDocument.path("version").asText());
        signature.put("groundTruthSchemaVersion",datasetDocument.path("groundTruthSchemaVersion").asInt());
        signature.put("javaVersion",System.getProperty("java.version"));
        String fingerprint=EvaluationFiles.hash(signature);
        Files.createDirectories(results); Path directory=results.resolve(id);
        if(!resume) {Files.createDirectory(directory); protect(directory);}
        else require(Files.isDirectory(directory),"Experiment not found");
        try(var channel=FileChannel.open(directory.resolve(".run.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            var lock=channel.tryLock()) {
            require(lock!=null,"Experiment already running");
            Path manifest=directory.resolve("experiment.json");
            if(resume) require(EvaluationFiles.JSON.readTree(EvaluationFiles.read(manifest)).path("fingerprint").asText().equals(fingerprint),"Resume conditions changed");
            else {
                signature.put("fingerprint",fingerprint); signature.put("experimentId",id); signature.put("createdAt",Instant.now().toString());
                signature.put("promptSchemaVersion",2); signature.put("matchingProtocol","literal-cause-evidence-v1");
                checkpoint(manifest,signature);
            }
            Generator generator=suppliedGenerator;
            Map<String,Generation> memory=new HashMap<>(); List<CaseResult> outcomes=new ArrayList<>();
            int totalDispatches=0,totalTools=0;
            try(var files=Files.list(directory)) {
                for(Path file:files.filter(p->p.toString().endsWith(".generation.json")).toList()) {
                    var g=readCheckpoint(file,Generation.class);
                    if(g.usage()!=null) {totalDispatches+=orZero(g.usage().modelDispatches());totalTools+=orZero(g.usage().toolInvocations());}
                }
            }
            String stop=Files.exists(directory.resolve("stop.json")) ? EvaluationFiles.JSON.readTree(EvaluationFiles.read(directory.resolve("stop.json"))).path("reason").asText() : null;
            try(var attempts=Files.list(directory)) {
                boolean interrupted=attempts.filter(p->p.toString().endsWith(".attempt.json"))
                    .anyMatch(p->!Files.exists(p.resolveSibling(p.getFileName().toString().replace(".attempt.json",".generation.json"))));
                if(interrupted) {
                    stop="UNKNOWN_USAGE_INTERRUPTED";
                    if(!Files.exists(directory.resolve("stop.json"))) checkpoint(directory.resolve("stop.json"),Map.of("reason",stop));
                }
            }
            for(var fixture:cases) for(Group group:groups) {
                String key=fixture.fixture().fixtureId()+"-"+group; Path resultFile=directory.resolve(key+".case.json");
                if(Files.exists(resultFile)) {
                    var completed=readCheckpoint(resultFile,CaseResult.class);
                    require(id.equals(completed.experimentId()) && completed.fixtureId().equals(fixture.fixture().fixtureId())
                        && completed.group()==group && completed.sourceHash().equals(fixture.fixture().sourceHash()),"Checkpoint identity mismatch");
                    outcomes.add(completed);continue;
                }
                Group generationGroup=group==Group.R2 ? Group.R1 : group;
                String generationKey=fixture.fixture().fixtureId()+"-"+generationGroup;
                Path cache=directory.resolve(generationKey+".generation.json"); Generation generation=memory.get(generationKey);
                Path attempt=directory.resolve(generationKey+".attempt.json");
                boolean reused=group==Group.R2;
                if(generation==null && Files.exists(cache)) {
                    generation=readCheckpoint(cache,Generation.class);
                    if(generation.rawOutput()==null && "COMPLETED".equals(generation.status())) generation=failure("ERROR","RAW_NOT_STORED_RESUME",generation.provenance());
                }
                if(generation==null) {
                    if(Files.exists(attempt)) generation=failure("ERROR","INTERRUPTED_GENERATION_NOT_RETRIED",c.mode().name());
                    else if(stop!=null) generation=failure("NOT_RUN_AFTER_"+stop,stop,c.mode().name());
                    else if(c.mode()==Mode.OFFLINE_REPLAY) {
                        String replay=c.replays().get(fixture.fixture().fixtureId()+":"+generationGroup);
                        require(replay!=null,"Missing replay for selected case/group");
                        generation=EvaluationFiles.JSON.readValue(EvaluationFiles.read(EvaluationFiles.inside(root,replay)),Generation.class);
                        require(Set.of("SYNTHETIC_FIXTURE","RECORDED_LLM_UNVERIFIED").contains(generation.provenance()),"Replay provenance required");
                    } else if(totalDispatches+Math.min(c.limits().maxModelCalls(),c.limits().maxRequestsPerCase())>c.limits().maxTotalRequests()
                            || totalTools+c.limits().maxToolCalls()>c.limits().maxTotalToolCalls()) {
                        generation=failure("BUDGET_EXHAUSTED","EXPERIMENT_BUDGET",c.mode().name());
                    } else {
                        if(generator==null) generator=EvaluationGeneration.generator(c);
                        checkpoint(attempt,Map.of("fixtureId",fixture.fixture().fixtureId(),"group",generationGroup,
                            "sourceHash",fixture.fixture().sourceHash(),"startedAt",Instant.now().toString(),
                            "maxModelDispatches",Math.min(c.limits().maxModelCalls(),c.limits().maxRequestsPerCase()),"maxToolInvocations",c.limits().maxToolCalls()));
                        try {generation=generator.generate(fixture,generationGroup,c.limits());}
                        catch(Exception e) {generation=failure("ERROR","GENERATION_FAILED",c.mode().name());}
                    }
                    generation=sanitize(generation);
                    if(generation.usage()!=null) {totalDispatches+=orZero(generation.usage().modelDispatches());totalTools+=orZero(generation.usage().toolInvocations());}
                    if(Set.of("BUDGET_EXHAUSTED","QUOTA_EXHAUSTED").contains(generation.status())) {
                        stop=generation.status(); if(!Files.exists(directory.resolve("stop.json"))) checkpoint(directory.resolve("stop.json"),Map.of("reason",stop));
                    }
                    memory.put(generationKey,generation);
                    checkpoint(cache,c.storeRawFindings() ? generation : new Generation(generation.status(),null,generation.observations(),generation.usage(),generation.generationLatencyMs(),generation.errorCode(),generation.provenance()));
                }
                require(EvaluationFiles.treeHash(fixture.sourceRoot()).equals(fixture.fixture().sourceHash()),"Source changed during experiment");
                var outcome=evaluate(c,id,fixture,group,generation,reused,decisions);
                checkpoint(resultFile,outcome); outcomes.add(outcome);
            }
            new EvaluationExporter().export(directory,c,outcomes);
            return List.copyOf(outcomes);
        }
    }
    private CaseResult evaluate(Config c,String id,LoadedCase fixture,Group group,Generation generation,boolean reused,List<Adjudication> decisions) throws Exception {
        long started=System.nanoTime();
        String hash=EvaluationFiles.hash(Map.of("fixtureId",fixture.fixture().fixtureId(),"sourceHash",fixture.fixture().sourceHash(),
            "generationGroup",group==Group.R2?Group.R1:group,"generation",generation));
        var inspected=EvaluationValidation.inspect(generation,fixture,group==Group.R2);
        var raw=inspected.findings(); boolean valid=inspected.outcome().schemaValid() && inspected.outcome().evidenceValid() && "COMPLETED".equals(generation.status());
        List<com.fasterxml.jackson.databind.JsonNode> accepted=List.of();
        if("COMPLETED".equals(generation.status()) && (group!=Group.R2 || valid)) accepted=raw;
        var acceptedEvidence=accepted.isEmpty() ? List.<Boolean>of() : inspected.evidence();
        var matcher=new FindingMatcher(); var scorer=new EvaluationScorer();
        Group annotationGroup=group==Group.R2?Group.R1:group;
        var rawMatch=matcher.match(raw,fixture.truth(),decisions,fixture.fixture().fixtureId(),annotationGroup,hash,inspected.evidence());
        // Adjudications refer to raw indices. R2 is whole-report rejection, never reindexes a partial list.
        var acceptedMatch=accepted.isEmpty() ? matcher.match(accepted,fixture.truth(),List.of(),fixture.fixture().fixtureId(),annotationGroup,hash,acceptedEvidence) : rawMatch;
        String status="COMPLETED".equals(generation.status()) && !inspected.outcome().schemaValid() ? "INVALID_REPORT" : generation.status();
        if(group==Group.R2 && !valid && "COMPLETED".equals(status)) status="INVALID_REPORT";
        return new CaseResult(id,fixture.fixture().fixtureId(),group,c.mode()==Mode.OFFLINE_REPLAY?null:c.model(),group==Group.R0?List.of():c.skills(),2,
            fixture.fixture().sourceHash(),hash,status,generation.errorCode(),generation.provenance(),reused,
            raw.size(),accepted.size(),raw.size()-accepted.size(),c.storeRawFindings()?raw:List.of(),c.storeRawFindings()?accepted:List.of(),
            c.storeRawFindings() && accepted.isEmpty()?raw:List.of(),inspected.outcome(),rawMatch,acceptedMatch,
            scorer.score(raw,fixture.truth(),rawMatch,inspected.evidence()),scorer.score(accepted,fixture.truth(),acceptedMatch,acceptedEvidence),generation.usage(),generation.generationLatencyMs(),(System.nanoTime()-started)/1_000_000);
    }
    private Map<String,String> replayHashes(Config c,List<LoadedCase> cases,List<Group> groups) throws IOException {
        Map<String,String> hashes=new TreeMap<>();
        if(c.mode()==Mode.OFFLINE_REPLAY) for(var f:cases) for(var group:groups) {
            String key=f.fixture().fixtureId()+":"+(group==Group.R2?Group.R1:group);
            String replay=c.replays().get(key); require(replay!=null,"Missing replay");
            hashes.put(key,EvaluationFiles.sha(EvaluationFiles.read(EvaluationFiles.inside(root,replay))));
        }
        return hashes;
    }
    static String frameworkHash() throws IOException {
        // Bind resume/comparison to evaluator code, production prompt, skills and relevant pipeline classes.
        var hashes=new TreeMap<String,String>();
        for(String base:List.of("src/main/java/prj/anhzxje/aiagent/evaluation","src/main/java/prj/anhzxje/aiagent/skills","src/main/resources/skills")) {
            try(var paths=Files.walk(Path.of(base))) {for(Path p:paths.filter(Files::isRegularFile).sorted().toList()) hashes.put(p.toString(),EvaluationFiles.sha(EvaluationFiles.read(p)));}
        }
        for(String file:List.of("pom.xml","src/main/resources/prompts/code-reviewer.md","src/main/java/prj/anhzxje/aiagent/review/ReviewReportValidator.java",
            "src/main/java/prj/anhzxje/aiagent/infrastructure/ai/LlmBudgetService.java","src/main/java/prj/anhzxje/aiagent/review/ReviewBudgetAdvisor.java","src/main/java/prj/anhzxje/aiagent/infrastructure/tools/SandboxTools.java"))
            hashes.put(file,EvaluationFiles.sha(EvaluationFiles.read(Path.of(file))));
        return EvaluationFiles.hash(hashes);
    }
    private static Generation sanitize(Generation g) {return new Generation(g.status(),EvaluationFiles.redact(g.rawOutput()),g.observations()==null?List.of():g.observations(),g.usage(),g.generationLatencyMs(),g.errorCode()==null?null:g.errorCode().replaceAll("[^A-Z0-9_:.-]","_"),g.provenance());}
    static Generation failure(String status,String code,String provenance) {return new Generation(status,null,List.of(),null,null,code,provenance);}
    static int orZero(Integer n) {return n==null?0:n;}
    private static void protect(Path dir) throws IOException {
        if(Files.getFileStore(dir).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(dir,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    }
    private static void checkpoint(Path file,Object value) throws IOException {
        require(!Files.exists(file),"Checkpoint already exists");
        Path staging=Files.createTempFile(file.getParent(),".checkpoint-",".tmp");
        Object content=file.toString().endsWith(".case.json") || file.toString().endsWith(".generation.json")
            ? Map.of("sha256",EvaluationFiles.hash(value),"data",value) : value;
        try {Files.write(staging,EvaluationFiles.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(content)); Files.move(staging,file,StandardCopyOption.ATOMIC_MOVE);}
        finally {Files.deleteIfExists(staging);}
    }
    private static <T> T readCheckpoint(Path file,Class<T> type) throws IOException {
        var envelope=EvaluationFiles.JSON.readTree(EvaluationFiles.read(file));
        require(envelope.has("data") && EvaluationFiles.hash(envelope.path("data")).equals(envelope.path("sha256").asText()),"Corrupt checkpoint checksum");
        return EvaluationFiles.JSON.treeToValue(envelope.path("data"),type);
    }
}
