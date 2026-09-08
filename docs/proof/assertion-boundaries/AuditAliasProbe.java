import io.brule.tasking.core.*;
import io.brule.tasking.repository.*;
import java.nio.file.*;
import java.util.*;

class AuditAliasProbe {
    public static void main(String[] args) throws Exception {
        var root=Path.of("build/audit-alias-probe").toAbsolutePath();
        var distribution=root.resolve("distribution/bootstrap"); Files.createDirectories(distribution);
        for(var name:List.of("taskctl","taskctl.ps1","taskctl.bat")) Files.writeString(distribution.resolve(name),"fixture launcher\n");
        var target=root.resolve("disposable-ledger");
        if(Files.exists(target)) throw new IllegalStateException("fresh fixture required");
        var lock="lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256="+"0".repeat(64)+"\n";
        Bootstrap.INSTANCE.apply(Bootstrap.INSTANCE.plan(target,"audit.probe","test",distribution.getParent(),lock,new Transition.AddRecords(List.of(),List.of(),List.of()),false,null));
        var ledger=new FileTaskLedger(target,new ProviderRegistry(List.of()));
        var change=ProfileCodec.INSTANCE.decodeChange((ObjectValue)YamlValues.INSTANCE.parse("""
          {"protocol":"taskctl.profile-change/1","reviewed_head":null,"profile":{"protocol":"taskctl.effective-profile/1","identity":"probe.profile/v1","bindings":{}},"audit":{"protocol":"taskctl.profile-audit/1","classification":"actor-assertion","actor":"probe","occurred_at":"2026-09-08T04:00:00Z","reason":"Observe constructor validity","evidence":{"first":"valid","second":"valid"}}}
          """.strip()).getValue());
        change.getAudit().getEvidence().clear();
        var after=LedgerTransitions.INSTANCE.evolve(ledger.snapshot(),new Transition.SetProfile(change));
        var revision=after.getProfileHistory().getRevisions().values().iterator().next();
        boolean reconstructable=true;
        try { ProfileCodec.INSTANCE.decodeRevision(ProfileCodec.INSTANCE.revision(revision)); }
        catch(IllegalArgumentException invalid) { reconstructable=false; }
        System.out.println("{\"reducer_accepted\":true,\"profile_reconstructable\":"+reconstructable+",\"profile_applied\":false}");
    }
}
