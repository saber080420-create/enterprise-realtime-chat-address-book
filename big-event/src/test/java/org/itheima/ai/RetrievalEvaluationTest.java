package org.itheima.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in small, synthetic embedding evaluation; no business database writes or DeepSeek calls. */
@EnabledIfSystemProperty(named="retrieval.eval", matches="true")
class RetrievalEvaluationTest {
    @Test void reportFrozenSyntheticRetrievalCasesWithoutTuningOnResults() throws Exception {
        var json = new ObjectMapper();
        var fixture = json.readTree(Files.readString(Path.of("../docs/fixtures/retrieval-eval.json")));
        var settings = new VectorSettings();
        var embeddings = new OllamaEmbeddings(json, settings);
        List<String> documents = new ArrayList<>(); fixture.get("documents").forEach(d -> documents.add(d.get("text").asText()));
        var vectors = embeddings.embed(documents, new ChatModelGateway.Cancellation());
        int positives=0, hits=0, negatives=0, rejected=0;
        List<Map<String,Object>> results = new ArrayList<>();
        for (var item : fixture.get("cases")) {
            long begin=System.nanoTime();
            var query=embeddings.embed(List.of(item.get("query").asText()),new ChatModelGateway.Cancellation()).get(0);
            int top=0; double score=-2;
            for(int i=0;i<vectors.size();i++) { double value=cosine(query,vectors.get(i)); if(value>score){score=value;top=i;} }
            int expected=item.get("expectedDocument").asInt();
            int actual=score>=settings.getMinScore()?fixture.get("documents").get(top).get("id").asInt():0;
            if(expected==0){negatives++;if(actual==0)rejected++;}else{positives++;if(actual==expected)hits++;}
            assertTrue(Double.isFinite(score));
            results.add(Map.of("id",item.get("id").asText(),"expected",expected,"actual",actual,"score",score,
                    "elapsedMs",(System.nanoTime()-begin)/1_000_000,"pass",expected==actual));
        }
        System.out.println("RETRIEVAL_EVAL " + json.writeValueAsString(Map.of("model",settings.getModel(),"threshold",settings.getMinScore(),
                "positiveHits",hits,"positiveCount",positives,"negativeRejected",rejected,"negativeCount",negatives,"cases",results)));
        // Quality misses are reported, not hidden by changing the test set/threshold until it passes.
        assertEquals(9,results.size());
    }
    private double cosine(float[] a,float[] b) {
        double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=(double)a[i]*b[i];aa+=(double)a[i]*a[i];bb+=(double)b[i]*b[i];}
        return dot/Math.sqrt(aa*bb);
    }
}
