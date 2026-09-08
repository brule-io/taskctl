package io.brule.tasking.core

/** Source facts are retained as historical assertions, never modern receipts. */
data class ImportSource(val id: RecordId, val path: String, val state: String,
                        val sha256: String, val contract: ContractDigest?)

data class ImportManifest(val repository: String, val revision: String, val adapter: String,
                          val adapterVersion: String, val files: Map<String, String>,
                          val sources: List<ImportSource>, val universe: DraftUniverse) {
    init {
        require(repository.isNotBlank() && revision.matches(Regex("[a-f0-9]{40}"))) { "exact source repository/revision required" }
        require(adapter.isNotBlank() && adapterVersion.isNotBlank())
        require(files.isNotEmpty() && files.keys.all { it.isNotBlank() }) { "source witness locators required" }
        val identities: List<RecordId> = universe.tasks.map { it.id } + universe.roadmaps.map { it.id } + universe.epics.map { it.id }
        require(identities.toSet() == sources.map { it.id }.toSet() && sources.distinctBy { it.id }.size == sources.size) { "import identities and provenance disagree" }
        require(sources.distinctBy { it.path }.size == sources.size) { "duplicate source path" }
        sources.forEach { source ->
            require(source.state in setOf("open", "closed") && source.path in files)
            require(source.sha256 == Canonical.sha256(files.getValue(source.path).toByteArray(Charsets.UTF_8))) { "source digest mismatch" }
            if (source.id is TaskId) {
                require(source.contract != null && universe.tasks.single { it.id == source.id }.state == source.state)
            } else require(source.contract == null)
        }
        require((universe.indexProblems() + DraftLifecycle.evaluate(universe.tasks).problems).isEmpty()) { "unsupported or invalid imported graph" }
    }
    val id: ImportId by lazy { ImportId.parseOrThrow(Canonical.digest("taskctl.import-manifest/1", ImportCodec.manifest(this))) }
}

data class ImportReview(val manifest: ImportId, val actor: String, val time: AssertionTime, val rationale: String) {
    init { require(actor.isNotBlank() && rationale.isNotBlank()) }
}
data class ImportAdmission(val manifest: ImportManifest, val review: ImportReview) {
    init { require(manifest.id == review.manifest) { "review does not address this import manifest" } }

    /** Re-establish the serialized admission boundary before a transition can
     * persist it. Kotlin read-only collections can still alias caller-owned
     * mutable collections; a cached ID must never bless changed source bytes,
     * provenance or projected records. The typed decoder checks every field. */
    fun validate() {
        val inspected = ImportCodec.decodeManifest(ImportCodec.manifest(manifest))
        require(inspected.id == manifest.id && inspected.id == review.manifest) {
            "import manifest changed after identity or review; construct and review a new admission"
        }
    }
}

object ImportCodec {
    private fun ObjectValue.exact(vararg names: String) { require(fields.keys == names.toSet()) { "unknown or missing import fields" } }
    private fun ObjectValue.objectAt(name: String) = fields[name] as? ObjectValue ?: error("$name must be an object")
    private fun ObjectValue.texts(name: String) = objectAt(name).fields.mapValues { (it.value as? StringValue)?.value ?: error("source text required") }
    private fun recordId(value: String): RecordId = when {
        value.startsWith("TASK.") -> TaskId.parseOrThrow(value)
        value.startsWith("ROADMAP.") -> RoadmapId.parseOrThrow(value)
        value.startsWith("EPIC.") -> EpicId.parseOrThrow(value)
        else -> error("invalid import identity")
    }
    fun manifest(value: ImportManifest): ObjectValue = obj(
        "protocol" to StringValue("taskctl.import-manifest/1"), "source_repository" to StringValue(value.repository),
        "source_revision" to StringValue(value.revision), "adapter" to StringValue(value.adapter), "adapter_version" to StringValue(value.adapterVersion),
        "source_native_protocol" to NullValue, "evidence_classification" to StringValue("historical-narrative-unverified"),
        "files" to stringMap(value.files),
        "sources" to ArrayValue(value.sources.sortedBy { it.id.value }.map { obj("id" to StringValue(it.id.value), "path" to StringValue(it.path),
            "state" to StringValue(it.state), "sha256" to StringValue(it.sha256), "contract" to optionalString(it.contract?.value)) }),
        "projection" to obj("contract" to StringValue("taskctl.seed/alpha1"), "tasks" to ArrayValue(value.universe.tasks.sortedBy { it.id }.map(NativeCodec::task)),
            "roadmaps" to ArrayValue(value.universe.roadmaps.sortedBy { it.id }.map(PlanningRecordCodec::encode)),
            "epics" to ArrayValue(value.universe.epics.sortedBy { it.id }.map(PlanningRecordCodec::encode))),
    )
    fun decodeManifest(value: ObjectValue): ImportManifest {
        value.exact("protocol", "source_repository", "source_revision", "adapter", "adapter_version", "source_native_protocol", "evidence_classification", "files", "sources", "projection")
        require(value.requiredString("protocol") == "taskctl.import-manifest/1" && value.fields["source_native_protocol"] == NullValue &&
            value.requiredString("evidence_classification") == "historical-narrative-unverified") { "unsupported import classification" }
        val records = NativeCodec.decodeSeed(value.objectAt("projection"))
        return ImportManifest(value.requiredString("source_repository"), value.requiredString("source_revision"), value.requiredString("adapter"), value.requiredString("adapter_version"),
            value.texts("files"), value.requiredArray("sources").map { item ->
                val source = item as? ObjectValue ?: error("source object required")
                source.exact("id", "path", "state", "sha256", "contract")
                ImportSource(recordId(source.requiredString("id")), source.requiredString("path"), source.requiredString("state"), source.requiredString("sha256"),
                    when (val contract = source.fields["contract"]) { NullValue -> null; is StringValue -> ContractDigest.parseOrThrow(contract.value); else -> error("source contract required") })
            }, DraftUniverse(records.tasks, records.roadmaps, records.epics))
    }
    fun review(value: ImportReview): ObjectValue = obj("protocol" to StringValue(value.time.version.protocol("taskctl.import-review/1", "taskctl.import-review/2")), "classification" to StringValue("actor-assertion"),
        "manifest" to StringValue(value.manifest.value), "actor" to StringValue(value.actor), value.time.version.field to StringValue(value.time.value), "rationale" to StringValue(value.rationale))
    fun decodeReview(value: ObjectValue): ImportReview {
        val time = AssertionTimeVersion.select(value.requiredString("protocol"), "taskctl.import-review/1", "taskctl.import-review/2")
        value.exact("protocol", "classification", "manifest", "actor", time.field, "rationale")
        require(value.requiredString("classification") == "actor-assertion")
        return ImportReview(ImportId.parseOrThrow(value.requiredString("manifest")), value.requiredString("actor"), time.decode(value), value.requiredString("rationale"))
    }
    fun admission(value: ImportAdmission): ObjectValue = obj("protocol" to StringValue("taskctl.import-admission/1"), "manifest" to manifest(value.manifest), "review" to review(value.review))
    fun decodeAdmission(value: ObjectValue): ImportAdmission {
        value.exact("protocol", "manifest", "review")
        require(value.requiredString("protocol") == "taskctl.import-admission/1")
        return ImportAdmission(decodeManifest(value.objectAt("manifest")), decodeReview(value.objectAt("review")))
    }
}
