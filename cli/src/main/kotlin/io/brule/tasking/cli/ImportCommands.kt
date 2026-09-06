package io.brule.tasking.cli

import io.brule.tasking.core.*
import io.brule.tasking.compatibility.FantastiktImport
import io.brule.tasking.repository.Bootstrap
import java.nio.file.Files
import java.nio.file.Path

internal object ImportCommands {
    private fun read(path: String): ObjectValue = YamlValues.parse(Files.readString(Path.of(path))).value as? ObjectValue ?: error("object required")
    fun run(args: NativeCommands.Arguments): ObjectValue {
        val action = args.positional.singleOrNull() ?: error("import inspect|plan|apply required")
        if (action in setOf("inspect", "plan")) {
            args.allow("--source", "--source-repository", "--source-revision", "--adapter", positions = 1)
            require(args.need("--adapter") == FantastiktImport.ID) { "unsupported import adapter" }
            val manifest = FantastiktImport.inspect(Path.of(args.need("--source")).toAbsolutePath().normalize(), args.need("--source-repository"), args.need("--source-revision"))
            return if (action == "plan") FantastiktImport.plan(manifest) else obj("adapter" to StringValue(manifest.adapter), "adapter_version" to StringValue(manifest.adapterVersion),
                "source_revision" to StringValue(manifest.revision), "manifest_id" to StringValue(manifest.id.value), "source_files" to integer(manifest.files.size),
                "tasks" to integer(manifest.universe.tasks.size), "closed_tasks" to integer(manifest.universe.tasks.count { it.state == "closed" }),
                "roadmaps" to integer(manifest.universe.roadmaps.size), "epics" to integer(manifest.universe.epics.size),
                "structural_frontier" to strings(manifest.universe.frontier().map { it.value }), "unsupported" to strings(emptyList()),
                "historical_evidence" to StringValue("preserved as historical-narrative-unverified; no native closure receipts or dependency observations are invented"))
        }
        require(action == "apply") { "unknown import action" }
        args.allow("--source", "--file", "--review", "--id", "--toolchain", "--plan", positions = 1)
        require("--repo" in args.options) { "import apply requires an explicit --repo target" }
        val input = read(args.need("--file"))
        val plan = if (input.fields.keys == setOf("api", "command", "repository", "result") && input.requiredString("command") == "import")
            input.fields["result"] as? ObjectValue ?: error("plan object required") else input
        val manifest = ImportCodec.decodeManifest(plan.fields["manifest"] as? ObjectValue ?: error("import plan manifest required"))
        require(manifest.adapter == FantastiktImport.ID && manifest.adapterVersion == FantastiktImport.VERSION) { "unsupported import adapter/version" }
        val current = FantastiktImport.inspect(Path.of(args.need("--source")).toAbsolutePath().normalize(), manifest.repository, manifest.revision)
        if (plan != FantastiktImport.plan(current)) throw RevisionConflict("import source or plan changed; inspect and review again")
        val admission = ImportAdmission(manifest, ImportCodec.decodeReview(read(args.need("--review"))))
        val distribution = ToolRuntime.distribution() ?: error("import apply requires a standalone distribution")
        val initialization = Bootstrap.plan(args.root(), args.need("--id"), NativeCommands.version, distribution,
            Files.readString(Path.of(args.need("--toolchain"))), adopt = true, imported = admission)
        val writes = initialization.files.entries.map { (path, source) -> obj("path" to StringValue(path), "before_sha256" to NullValue,
            "after_sha256" to StringValue(Canonical.sha256(source.toByteArray(Charsets.UTF_8)))) }
        val result = if ("--plan" in args.options) initialization.result() else Bootstrap.apply(initialization)
        return ObjectValue(result.fields + mapOf("manifest_id" to StringValue(manifest.id.value), "writes" to ArrayValue(writes),
            "historical_evidence" to StringValue("historical-narrative-unverified; reconcile explicitly to establish current inputs")))
    }
}
