package io.brule.tasking.compatibility

import io.brule.tasking.core.*
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.task.LedgerState
import io.brule.workflow.task.TaskLedger
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Named, bounded conversion of the donor dialect; schema labels never select it. */
object FantastiktImport {
    const val ID = FantastiktAdapter.ID
    const val VERSION = "0.2.0"
    fun inspect(root: Path, repository: String, revision: String): ImportManifest {
        val layout = RepositoryLayout.at(root)
        require(!Files.exists(root.resolve(".agents/config.toml"))) { "source already has a native repository marker" }
        fun files(): Map<String, String> = Files.walk(root.resolve(".agents")).use { paths ->
            paths.sorted().filter { !Files.isDirectory(it, NOFOLLOW_LINKS) }.toList().filter { it.fileName.toString() != ".lock" }.associate { path ->
                require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, NOFOLLOW_LINKS)) { "unsupported source entry" }
                val relative = root.relativize(path).toString().replace('\\', '/')
                require(relative.startsWith(".agents/") && relative.matches(Regex("[A-Za-z0-9._/-]+")) &&
                    relative.split('/').none { it in setOf("", ".", "..") }) { "unsafe source entry" }
                relative to Files.readString(path)
            }
        }
        val before = files()
        val ledger = TaskLedger(layout).load()
        require(ledger.report.isValid) { ledger.report.errors.joinToString("\n") { it.render(layout.root) } }
        require(ledger.tasks.isNotEmpty()) { "source has no tasks for this adapter" }
        require(ledger.roadmaps.all { it.state == LedgerState.OPEN } && ledger.epics.all { it.state == LedgerState.OPEN }) {
            "closed planning records need an explicit native audit policy; this adapter cannot admit them"
        }
        val tasks = ledger.tasks.map { task ->
            val realizedBy = ledger.tasks.filter { it.realizes == task.ref }.map { TaskId.parseOrThrow(it.ref.value) }
            DraftRecord(TaskId.parseOrThrow(task.ref.value), task.title, task.state.name.lowercase(),
                task.markdown.section("Description")!!.content.trim(),
                (task.depends.map { TaskId.parseOrThrow(it.value) } + realizedBy).distinct().sorted(),
                listOf(task.markdown.section("Requirements")!!.content.trim()), listOf(task.markdown.section("Deliverables")!!.content.trim()),
                emptyList(), obj("legacy.fantastikt/v1" to obj("effort" to StringValue(task.effort.name), "impact" to StringValue(task.impact.name),
                    "roadmap" to StringValue(task.roadmap.value), "realizes" to optionalString(task.realizes?.value))), "tasking/core-draft-2")
        }
        val roadmaps = ledger.roadmaps.map { roadmap ->
            DraftRoadmap(RoadmapId.parseOrThrow(roadmap.ref.value), roadmap.title,
                listOf("Description", "Outcomes", "Exit Criteria").joinToString("\n\n") { "## $it\n\n" + roadmap.markdown.section(it)!!.content.trim() },
                ledger.tasks.filter { it.roadmap == roadmap.ref }.map { TaskId.parseOrThrow(it.ref.value) }, extensions = obj("legacy.fantastikt/v1" to
                    obj("ordinal" to integer(roadmap.ordinal), "source_epic" to optionalString(roadmap.epic?.value), "source_state" to StringValue("open"))))
        }
        val epics = ledger.epics.map { epic ->
            val lanes = ledger.roadmaps.filter { it.epic == epic.ref }.map { it.ref }.toSet()
            DraftEpic(EpicId.parseOrThrow(epic.ref.value), epic.title,
                listOf("Description", "Outcomes", "Boundaries").joinToString("\n\n") { "## $it\n\n" + epic.markdown.section(it)!!.content.trim() },
                ledger.tasks.filter { it.roadmap in lanes }.map { TaskId.parseOrThrow(it.ref.value) }, extensions = obj("legacy.fantastikt/v1" to obj("source_state" to StringValue("open"))))
        }
        fun source(id: RecordId, path: Path, state: LedgerState, contract: ContractDigest? = null): ImportSource {
            val name = layout.display(path)
            return ImportSource(id, name, state.name.lowercase(), Canonical.sha256(before.getValue(name).toByteArray(Charsets.UTF_8)), contract)
        }
        val sources = ledger.tasks.map { source(TaskId.parseOrThrow(it.ref.value), it.path, it.state, ContractDigest.parseOrThrow(FantastiktAdapter.contract(it))) } +
            ledger.roadmaps.map { source(RoadmapId.parseOrThrow(it.ref.value), it.path, it.state) } + ledger.epics.map { source(EpicId.parseOrThrow(it.ref.value), it.path, it.state) }
        if (before != files()) throw RevisionConflict("source changed while inspecting; retry")
        val result = ImportManifest(repository, revision, ID, VERSION, before, sources.sortedBy { it.id.value }, DraftUniverse(tasks, roadmaps, epics))
        require(result.universe.frontier().toSet() == ledger.graph.frontier.map { TaskId.parseOrThrow(it.ref.value) }.toSet()) { "conversion changed structural frontier" }
        return result
    }
    fun plan(value: ImportManifest): ObjectValue = obj("protocol" to StringValue("taskctl.import-plan/1"), "manifest_id" to StringValue(value.id.value),
        "manifest" to ImportCodec.manifest(value), "target_protocol" to StringValue("taskctl.native/alpha3"),
        "structural_frontier" to strings(value.universe.frontier().map { it.value }),
        "contracts" to ObjectValue(value.universe.tasks.sortedBy { it.id }.associate { it.id.value to StringValue(DraftLifecycle.contract(it).value) }),
        "currency_policy" to StringValue("Historical closure is not a native receipt; dependency observations are absent until explicit reconciliation. Structural frontier does not confer currency."))
}
