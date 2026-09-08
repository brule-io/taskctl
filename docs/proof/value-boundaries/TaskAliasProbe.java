import io.brule.tasking.core.DraftDocument;
import io.brule.tasking.core.StringValue;
import io.brule.tasking.core.Transition;
import io.brule.tasking.repository.Bootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class TaskAliasProbe {
    public static void main(String[] args) throws Exception {
        var root=Path.of("build/task-alias-probe").toAbsolutePath();
        var distribution=root.resolve("distribution/bootstrap"); Files.createDirectories(distribution);
        for (var name:List.of("taskctl","taskctl.ps1","taskctl.bat")) Files.writeString(distribution.resolve(name),"probe launcher\n");
        var target=root.resolve("unapplied-target");
        if (Files.exists(target)) throw new IllegalStateException("probe target must remain absent");
        var task=DraftDocument.Companion.parse("""
            {"protocol":"tasking/core-draft-2","id":"TASK.probe.alias","title":"Alias probe","state":"open","intent":"Test a typed boundary","requires":[],"requirements":["Remain reconstructable"],"acceptance":["Valid namespace"],"required_extensions":[],"extensions":{"probe.optional/v1":{},"probe.second/v1":{}},"verification":["test"]}
            """.strip()).getRecord();
        task.getExtensions().getFields().put("banana",new StringValue("inserted after constructor validation"));
        var lock="lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256="+"0".repeat(64)+"\n";
        var plan=Bootstrap.INSTANCE.plan(target,"alias.probe","test",distribution.getParent(),lock,new Transition.AddRecords(List.of(task),List.of(),List.of()),false,null);
        var encoded=plan.getFiles().entrySet().stream().filter(e->e.getKey().startsWith(".agents/tasks/")).findFirst().orElseThrow().getValue();
        boolean reconstructable=true;
        try { DraftDocument.Companion.parse(encoded); } catch (IllegalArgumentException failure) { reconstructable=false; }
        System.out.println("{\"plan_created\":true,\"task_reconstructable\":"+reconstructable+",\"plan_applied\":false,\"target_exists\":"+Files.exists(target)+"}");
    }
}
