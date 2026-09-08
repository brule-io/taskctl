package io.brule.tasking.core

data class ProviderPin(val provider: ProviderId, val version: ProviderVersion, val digest: ProviderDigest)

/** Installation never activates a feature. This complete, persisted value does. */
data class EffectiveProfile(val identity: ProfileId, val bindings: Map<ExtensionId, ProviderPin>) {
    val digest: ProfileDigest get() = ProfileDigest.parseOrThrow(Canonical.digest("taskctl.effective-profile/1", ProfileCodec.profile(this)))
}

data class ProfileAudit(val actor: String, val occurredAt: OccurredAt, val reason: String, val evidence: Map<String, String>) {
    init {
        require(actor.isNotBlank() && reason.isNotBlank()) { "profile actor and reason are required" }
        require(evidence.isNotEmpty() && evidence.all { (key, value) -> key.isNotBlank() && value.isNotBlank() }) { "profile audit evidence is required" }
    }
}

data class ProfileChange(val reviewedHead: ProfileRevisionId?, val profile: EffectiveProfile, val audit: ProfileAudit)
data class ProfileRevision(val parent: ProfileRevisionId?, val ledger: Revision, val profile: EffectiveProfile, val audit: ProfileAudit) {
    val id: ProfileRevisionId get() = ProfileRevisionId.parseOrThrow(Canonical.digest("taskctl.profile-revision/1", ProfileCodec.revision(this)))
}
data class ProfileHistory(val origin: Revision, val head: ProfileRevisionId, val revisions: Map<ProfileRevisionId, ProfileRevision>) {
    val profile: EffectiveProfile get() = revisions.getValue(head).profile
    fun validate() {
        require(revisions.isNotEmpty() && head in revisions) { "profile HEAD absent" }
        revisions.forEach { (id, value) ->
            require(id == value.id) { "profile revision digest mismatch" }
            require(value.parent == null || value.parent in revisions) { "profile parent absent" }
            if (value.parent == null) require(value.ledger == origin) { "profile baseline origin mismatch" }
        }
        val seen = mutableSetOf<ProfileRevisionId>()
        var cursor: ProfileRevisionId? = head
        while (cursor != null) {
            require(seen.add(cursor)) { "profile revision cycle" }
            cursor = revisions.getValue(cursor).parent
        }
        require(seen == revisions.keys) { "profile history has unreferenced revisions" }
    }
}

/** Self-contained historical semantics; verification never loads old code. */
data class RevisionSemantics(val profile: EffectiveProfile, val contributedPrerequisites: List<TaskId>) {
    init { require(contributedPrerequisites.distinct().size == contributedPrerequisites.size) { "duplicate contributed prerequisite" } }
}

/** Providers see only data included in the effective semantic contract. */
class ProviderTask internal constructor(val id: TaskId, val core: ObjectValue, val payload: Value, val contract: ContractDigest)

/** Explicitly injected deterministic code. It has no lifecycle, raw-record or storage API. */
interface PinnedSemanticProvider {
    val feature: ExtensionId
    val pin: ProviderPin
    fun evaluate(record: ProviderTask): Contribution
    fun verify(record: ProviderTask, evidence: Map<String, String>): List<String>
}

/** Transient adapter dependency, never serialized as ledger state or auto-loaded. */
data class ProviderRegistry(val providers: List<PinnedSemanticProvider> = emptyList()) {
    init { require(providers.distinctBy { it.feature }.size == providers.size) { "duplicate provider feature identity" } }
    internal fun resolved(profile: EffectiveProfile): List<SemanticProvider> = providers.filter { profile.bindings[it.feature] == it.pin }.map { provider ->
        object : SemanticProvider {
            override val identity = provider.feature.value
            override val pin = pinKey(provider.pin)
            private fun input(record: DraftRecord): ProviderTask = ProviderTask(record.id,
                if (record.protocol == "tasking/core-draft-2") DraftLifecycle.projection(record) else DraftLifecycle.legacyProjection(record),
                record.extensions.fields[provider.feature.value] ?: NullValue, effectiveContract(record, profile))
            override fun evaluate(record: DraftRecord): Contribution = provider.evaluate(input(record))
            override fun verify(record: DraftRecord, evidence: Map<String, String>): List<String> = provider.verify(input(record), evidence)
        }
    }
}

private fun pinKey(pin: ProviderPin): String = Canonical.digest("taskctl.provider-pin/1", ProfileCodec.pin(pin))
internal fun EffectiveProfile.legacyEvaluationProfile(): Profile = Profile(bindings.entries.associate { it.key.value to pinKey(it.value) })

fun effectiveContract(record: DraftRecord, profile: EffectiveProfile?): ContractDigest {
    if (profile == null) return DraftLifecycle.contract(record)
    return ContractDigest.parseOrThrow(Canonical.digest("taskctl.semantic-contract/3", obj(
        "base_contract" to StringValue(DraftLifecycle.contract(record).value), "profile" to ProfileCodec.profile(profile),
        "active_extensions" to ObjectValue(profile.bindings.keys.sortedBy { it.value }.associate { it.value to (record.extensions.fields[it.value] ?: NullValue) }))))
}

fun LedgerSnapshot.effectiveContract(record: DraftRecord): ContractDigest = effectiveContract(record, profileHistory?.profile)
fun LedgerSnapshot.effectiveEvaluation(): DraftEvaluation = profileHistory?.profile?.let { profile ->
    val evaluation = DraftLifecycle.evaluate(universe.tasks, profile.legacyEvaluationProfile(), providers.resolved(profile))
    val missing = profile.bindings.filter { (feature, pin) -> providers.providers.none { it.feature == feature && it.pin == pin } }.keys
    evaluation.copy(problems = (evaluation.problems + missing.map { "required semantic provider unavailable or unpinned: $it" }).distinct().sorted())
} ?: DraftLifecycle.evaluate(universe.tasks)

fun LedgerSnapshot.semanticProblems(): List<String> = (effectiveEvaluation().problems +
    universe.planningRecords.flatMap { it.requiredExtensions }.distinct().map { "required planning provider unavailable in this draft: $it" }).distinct().sorted()

fun LedgerSnapshot.structuralProblems(): List<String> = (universe.indexProblems() + effectiveEvaluation().problems.filterNot(::providerUnavailable)).distinct().sorted()

internal fun LedgerSnapshot.revisionSemantics(task: TaskId, evaluation: DraftEvaluation = effectiveEvaluation()): RevisionSemantics? =
    profileHistory?.profile?.let { RevisionSemantics(it, evaluation.contributions.getValue(task).prerequisites.sorted()) }

internal fun providerUnavailable(message: String): Boolean = "required semantic provider unavailable" in message || "provider evaluation failed" in message

internal fun LedgerSnapshot.changeProfile(change: ProfileChange): LedgerSnapshot {
    require(history != null) { "track task history before effective profile adoption" }
    require(change.reviewedHead == profileHistory?.head) { "profile change does not address current profile HEAD" }
    require(change.profile != profileHistory?.profile) { "profile change makes no change" }
    val entry = ProfileRevision(profileHistory?.head, revision, change.profile, change.audit)
    val updated = ProfileHistory(profileHistory?.origin ?: revision, entry.id, profileHistory?.revisions.orEmpty() + (entry.id to entry))
    return copy(profileHistory = updated)
}

fun LedgerSnapshot.validateProfiles() {
    profileHistory?.validate()
    val known = profileHistory?.revisions?.values.orEmpty().map { it.profile.digest }.toSet()
    history?.revisions?.values.orEmpty().forEach { revision ->
        revision.semantics?.let { require(it.profile.digest in known) { "task revision profile absent from profile history" } }
        revision.review?.profile?.let { require(it in known) { "review profile absent from profile history" } }
        if (revision.review?.outcome == ReviewOutcome.REVALIDATED && revision.review.profile != null) {
            require(revision.review.profile == revision.semantics?.profile?.digest) { "revalidated review disagrees with captured profile" }
        }
    }
}

internal fun LedgerSnapshot.profiledClosureProblems(evidence: ClosureEvidence): List<String> {
    val profile = profileHistory?.profile ?: return universe.closureProblems(evidence.receipt.taskId, evidence.receipt)
    val task = universe.tasks.singleOrNull { it.id == evidence.receipt.taskId } ?: return listOf("unknown task: ${evidence.receipt.taskId}")
    // Reuse the existing guards, substituting only its historical digest check.
    val legacyReceipt = evidence.receipt.copy(contractDigest = DraftLifecycle.contract(task, profile.legacyEvaluationProfile()))
    val errors = universe.closureProblems(task.id, legacyReceipt, profile.legacyEvaluationProfile(), providers.resolved(profile))
    return errors + if (evidence.receipt.contractDigest != effectiveContract(task)) listOf("receipt does not address current effective contract") else emptyList()
}
