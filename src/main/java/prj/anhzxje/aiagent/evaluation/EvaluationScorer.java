package prj.anhzxje.aiagent.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

public final class EvaluationScorer {
    public static Double ratio(long n,long d) { return d==0 ? null : n/(double)d; }
    public static Counts counts(int tp,int fp,int fn) {
        return new Counts(tp,fp,fn,ratio(tp,tp+(long)fp),ratio(tp,tp+(long)fn),ratio(2L*tp,2L*tp+fp+fn));
    }
    public Scores score(List<JsonNode> predictions,List<Defect> truth,MatchResult matching,List<Boolean> evidence) {
        Map<String,int[]> categories=new TreeMap<>(); Map<String,int[]> families=new TreeMap<>();
        Map<String,Defect> byId=new HashMap<>(); truth.forEach(t->byId.put(t.defectId(),t));
        for(var t:truth) if(t.classification()==Label.CONFIRMED_BUG) {
            categories.computeIfAbsent(t.category(),k->new int[3]);
            families.computeIfAbsent(t.family(),k->new int[2])[1]++;
        }
        int locations=0,severity=0;
        for(var m:matching.matches()) {
            var t=byId.get(m.defectId()); categories.get(t.category())[0]++;
            families.get(t.family())[0]++;
            var p=predictions.get(m.predictionIndex());
            if(FindingMatcher.location(p,t,0)) locations++;
            if(t.severity().equals(p.path("severity").asText())) severity++;
        }
        for(int i:matching.falsePositives()) categories.computeIfAbsent(predictions.get(i).path("category").asText("UNKNOWN"),k->new int[3])[1]++;
        for(String id:matching.falseNegatives()) categories.get(byId.get(id).category())[2]++;
        Map<String,Counts> categoryScores=new TreeMap<>(); categories.forEach((k,v)->categoryScores.put(k,counts(v[0],v[1],v[2])));
        Map<String,FamilyDetection> detection=new TreeMap<>(); families.forEach((k,v)->detection.put(k,new FamilyDetection(v[0],v[1],ratio(v[0],v[1]))));
        int tp=matching.matches().size();
        return new Scores(counts(tp,matching.falsePositives().size(),matching.falseNegatives().size()),categoryScores,detection,
            matching.duplicates().size(),matching.candidates().size(),!matching.candidates().isEmpty(),
            ratio(evidence.stream().filter(Boolean.TRUE::equals).count(),predictions.size()),ratio(locations,tp),ratio(severity,tp));
    }
}
