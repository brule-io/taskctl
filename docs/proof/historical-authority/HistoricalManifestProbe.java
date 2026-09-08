import io.brule.tasking.core.*;
import io.brule.tasking.compatibility.HistoricalImport;
import java.nio.file.*;
import java.util.*;

class HistoricalManifestProbe {
    public static void main(String[] args) throws Exception {
        var base=Path.of("conformance/src/test/resources/daemon");
        var index=(ObjectValue)YamlValues.INSTANCE.parse(Files.readString(base.resolve("sources.json"))).getValue();
        Map<String,List<HistoricalRecord>> groups=new TreeMap<>();
        for(var entry:index.requiredArray("records")) {
            var value=(ObjectValue)entry;
            var source=new SourceProvenance(value.requiredString("repository"),value.requiredString("revision"),value.requiredString("source_path"),value.requiredString("source_sha256"),value.requiredString("adapter"),value.requiredString("adapter_version"));
            var record=HistoricalImport.INSTANCE.preview(Files.readAllBytes(base.resolve(value.requiredString("file"))),source);
            groups.computeIfAbsent(source.getRepository(),unused->new ArrayList<>()).add(record);
        }
        Map<String,Value> manifests=new LinkedHashMap<>();
        for(var entry:groups.entrySet()) manifests.put(entry.getKey(),HistoricalImport.INSTANCE.manifest(entry.getValue()));
        System.out.println(Json.INSTANCE.encode(new ObjectValue(manifests)));
    }
}
