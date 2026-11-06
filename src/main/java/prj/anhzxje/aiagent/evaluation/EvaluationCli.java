package prj.anhzxje.aiagent.evaluation;

import java.nio.file.*;
import java.util.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationDataset.require;

public final class EvaluationCli {
    private EvaluationCli() {}
    public static void main(String[] args) {System.exit(execute(args));}
    public static int execute(String[] args) {
        try {run(args);return 0;}
        catch(Exception error) {System.err.println("Evaluation refused/failed: "+error.getClass().getSimpleName()+". Check paths, schema, pinned conditions and approval; raw error/source text is withheld.");return 2;}
    }
    private static void run(String[] args) throws Exception {
        Map<String,String> options=new HashMap<>();
        Set<String> switches=Set.of("--dry-run","--resume","--real-llm","--help");
        Set<String> values=Set.of("--config","--id","--groups","--subset","--max-cases","--model","--mode","--confirm","--approval-file","--compare","--comparison-id");
        for(int i=0;i<args.length;i++) {
            String key=args[i]; require(!options.containsKey(key),"Duplicate CLI option");
            if(switches.contains(key)) options.put(key,"true");
            else {require(values.contains(key) && i+1<args.length,"Unknown CLI option/missing value");options.put(key,args[++i]);}
        }
        if(options.containsKey("--help")) {System.out.println("Evaluation CLI: --config configs/offline-development.json --id NAME [--groups R0,R1,R2] [--subset ID,...] [--max-cases N] [--model ID] [--mode OFFLINE_REPLAY|MOCK_INTEGRATION|REAL_LLM] [--dry-run] [--resume]\nReal runs additionally require --real-llm --approval-file FILE --confirm APPROVE_REAL_LLM:NAME:CONFIG_HASH. Compare: --compare ID1,ID2 --comparison-id NAME. Offline is default in shipped configurations.");return;}
        Path root=Path.of("evaluation");
        if(options.containsKey("--compare")) {
            String id=options.get("--comparison-id");require(id!=null && id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"),"Comparison ID required");
            new ExperimentComparison().compare(root.resolve("results"),List.of(options.get("--compare").split(",")),root.resolve("reports").resolve(id));
            System.out.println("Comparison exported: evaluation/reports/"+id);return;
        }
        var original=ExperimentConfiguration.load(root,options.getOrDefault("--config","configs/offline-development.json"));
        var config=new Config(original.schemaVersion(),original.dataset(),Mode.valueOf(options.getOrDefault("--mode",original.mode().name())),original.provider(),
            options.getOrDefault("--model",original.model()),original.modelOptions(),original.tools(),original.skills(),original.limits(),original.replays(),original.adjudications(),original.storeRawFindings(),original.pricing());
        ExperimentConfiguration.validate(config);
        var groups=Arrays.stream(options.getOrDefault("--groups","R0,R1,R2").split(",")).map(Group::valueOf).distinct().sorted().toList();
        Set<String> subset=options.containsKey("--subset")?Set.of(options.get("--subset").split(",")):Set.of();
        int maximum=Integer.parseInt(options.getOrDefault("--max-cases",Integer.toString(config.limits().maxCases())));
        var runner=new EvaluationRunner(root,root.resolve("results"),null);
        if(options.containsKey("--dry-run")) {System.out.println(EvaluationFiles.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(runner.preview(config,groups,subset,maximum)));return;}
        if(config.mode()==Mode.REAL_LLM) require(options.containsKey("--real-llm"),"Explicit real flag required in addition to exact approval");
        String id=options.get("--id");
        var results=runner.run(config,id,groups,subset,maximum,options.containsKey("--resume"),options.get("--confirm"),
            options.containsKey("--approval-file")?Path.of(options.get("--approval-file")):null);
        System.out.println("Exported "+results.size()+" observations to evaluation/results/"+id+"; mode="+config.mode()+". No model-quality conclusion is implied.");
    }
}
