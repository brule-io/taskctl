package io.brule.tasking.repository

import io.brule.tasking.core.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

/** The native filesystem adapter owns all layout, locking and journal behavior.
 * Read commands never create a lock, cache or journal inside the consumer. */
class FileTaskLedger(repository: Path) : TaskLedger {
    val root: Path = NativeFiles.repositoryRoot(repository)
    private val journal = ".agents/runtime/transaction.json"
    private data class Loaded(val snapshot: LedgerSnapshot, val documents: Map<TaskId, DraftDocument>,
                              val taskPaths: Map<TaskId, String>, val contents: Map<String, String>)

    override fun snapshot(): LedgerSnapshot = load().snapshot

    private fun contents(): Map<String, String> {
        check(!Files.exists(NativeFiles.locate(root, ".taskctl/bootstrap-journal.json"))) { "interrupted bootstrap: run taskctl recover" }
        check(!Files.exists(NativeFiles.locate(root, journal))) { "interrupted transaction: run taskctl recover" }
        val files = linkedMapOf<String, String>()
        for (name in listOf(".agents/config.toml", ".agents/policy.toml")) files[name] = Files.readString(NativeFiles.locate(root, name))
        for (name in listOf("tasks", "roadmaps", "epics", "receipts")) files += NativeFiles.regularFiles(root, ".agents/$name")
        check(!Files.exists(NativeFiles.locate(root, journal))) { "concurrent transaction: retry after recovery/completion" }
        return files
    }

    private fun load(): Loaded {
        val files = contents()
        if (files != contents()) throw RevisionConflict("ledger changed while reading; retry")
        val config = NativeFiles.flatToml(files.getValue(".agents/config.toml"), setOf("contract", "protocol", "repository_id", "profile"))
        require(config["contract"] == "taskctl.repository/alpha1" && config["protocol"] == "taskctl.native/alpha1") { "unsupported repository contract; no automatic migration" }
        require(config["repository_id"]!!.isNotBlank() && config["profile"] == "minimal/alpha1") { "unsupported profile or missing identity" }
        val policy = NativeFiles.flatToml(files.getValue(".agents/policy.toml"), setOf("contract", "profile"))
        require(policy["contract"] == "taskctl.policy/alpha1" && policy["profile"] == config["profile"]) { "unsupported policy" }
        val taskFiles = files.filterKeys { it.startsWith(".agents/tasks/") }.mapValues { DraftDocument.parse(it.value) }
        val roadmaps = files.filterKeys { it.startsWith(".agents/roadmaps/") }.values.map {
            PlanningDocument.parse(it).record as? DraftRoadmap ?: error("roadmap kind required")
        }
        val epics = files.filterKeys { it.startsWith(".agents/epics/") }.values.map {
            PlanningDocument.parse(it).record as? DraftEpic ?: error("epic kind required")
        }
        val receipts = files.filterKeys { it.startsWith(".agents/receipts/") }.values.map { NativeCodec.decodeEvidence(NativeFiles.objectValue(it)) }
        val universe = DraftUniverse(taskFiles.values.map { it.record }, roadmaps, epics)
        val errors = universe.indexProblems() + DraftLifecycle.evaluate(universe.tasks).problems.filterNot { "required semantic provider unavailable" in it }
        require(errors.isEmpty()) { errors.joinToString("\n") }
        universe.tasks.filter { it.state == "closed" }.forEach { task ->
            require(receipts.any { DraftLifecycle.addresses(it.receipt, task) }) { "closed task lacks evidence for its current contract: ${task.id}" }
        }
        val revision = Revision.parseOrThrow(Canonical.digest("taskctl.file-revision/alpha1", stringMap(files.mapValues { Canonical.sha256(it.value.toByteArray()) })))
        return Loaded(LedgerSnapshot(config.getValue("repository_id"), revision, universe, receipts),
            taskFiles.values.associateBy { it.record.id }, taskFiles.entries.associate { it.value.record.id to it.key }, files)
    }

    override fun apply(expectedRevision: Revision, transition: Transition): TransitionResult = locked {
        val before = load()
        if (before.snapshot.revision != expectedRevision) throw RevisionConflict("stale ledger revision; inspect again before applying")
        LedgerTransitions.reduce(before.snapshot, transition)
        val writes = linkedMapOf<String, String>()
        val changed = mutableListOf<RecordId>()
        when (transition) {
            is Transition.AddRecords -> {
                transition.tasks.forEach { task ->
                    writes[".agents/tasks/" + NativeFiles.fileName(task.id.value)] = Json.encode(NativeCodec.task(task)) + "\n"; changed += task.id
                }
                transition.roadmaps.forEach { roadmap ->
                    writes[".agents/roadmaps/" + NativeFiles.fileName(roadmap.id.value)] = Json.encode(PlanningRecordCodec.encode(roadmap)) + "\n"; changed += roadmap.id
                }
                transition.epics.forEach { epic ->
                    writes[".agents/epics/" + NativeFiles.fileName(epic.id.value)] = Json.encode(PlanningRecordCodec.encode(epic)) + "\n"; changed += epic.id
                }
                require(writes.keys.none { Files.exists(NativeFiles.locate(root, it)) }) { "record locator collision" }
            }
            is Transition.CloseTask -> {
                val evidence = transition.evidence
                val task = evidence.receipt.taskId
                writes[before.taskPaths.getValue(task)] = before.documents.getValue(task).withState("closed").render()
                val encoded = Json.encode(NativeCodec.evidence(evidence)) + "\n"
                writes[".agents/receipts/" + Canonical.sha256(encoded.toByteArray()) + ".json"] = encoded
                changed += task
            }
        }
        if (writes.isNotEmpty()) {
            val operation = obj("contract" to StringValue("taskctl.transaction/alpha1"),
                "before" to ObjectValue(writes.keys.associateWith { optionalString(before.contents[it]) }), "after" to stringMap(writes))
            NativeFiles.atomicWrite(root, journal, Json.encode(operation))
            finish(operation)
        }
        TransitionResult(load().snapshot.revision, changed.sortedBy { it.value })
    }

    fun recover(): Revision = locked {
        val path = NativeFiles.locate(root, journal)
        if (Files.exists(path)) finish(NativeFiles.objectValue(Files.readString(path)))
        load().snapshot.revision
    }

    private fun finish(operation: ObjectValue) {
        require(operation.fields.keys == setOf("contract", "before", "after") && operation.requiredString("contract") == "taskctl.transaction/alpha1") { "invalid transaction journal" }
        val before = operation.fields["before"] as? ObjectValue ?: error("invalid journal before image")
        val after = operation.fields["after"] as? ObjectValue ?: error("invalid journal after image")
        require(before.fields.keys == after.fields.keys) { "journal images disagree" }
        val writes = after.fields.mapValues { (it.value as? StringValue)?.value ?: error("invalid journal content") }
        // Validate EVERY preimage before writing any file, including on recovery.
        for ((relative, content) in writes) {
            require(Regex("^\\.agents/(tasks|roadmaps|epics|receipts)/[A-Za-z0-9._/-]+$").matches(relative)) { "journal path outside writable record roots" }
            val path = NativeFiles.locate(root, relative)
            val current = if (Files.exists(path)) StringValue(Files.readString(path)) else NullValue
            require(current == before.fields.getValue(relative) || current == StringValue(content)) { "external edit conflicts with transaction: $relative" }
        }
        writes.forEach { (path, content) -> NativeFiles.atomicWrite(root, path, content) }
        Files.delete(NativeFiles.locate(root, journal))
    }

    private fun <T> locked(action: () -> T): T {
        val path = NativeFiles.locate(root, ".agents/runtime/lock")
        Files.createDirectories(path.parent)
        return FileChannel.open(path, CREATE, WRITE).use { channel ->
            val lock = channel.tryLock() ?: throw RevisionConflict("ledger writer is active; retry")
            lock.use { action() }
        }
    }
}
