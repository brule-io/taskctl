package io.brule.tasking.core

/** Strict profile envelopes. Historical Profile/string-map hashes are untouched. */
object ProfileCodec {
    private fun ObjectValue.exact(vararg names: String) { require(fields.keys == names.toSet()) { "unknown or missing profile fields" } }
    private fun ObjectValue.objectAt(name: String): ObjectValue = fields[name] as? ObjectValue ?: error("$name must be an object")
    private fun ObjectValue.nullableText(name: String): String? = when (val value = fields.getValue(name)) {
        NullValue -> null
        is StringValue -> value.value
        else -> error("$name must be a string or null")
    }
    fun pin(value: ProviderPin): ObjectValue = obj("provider" to StringValue(value.provider.value),
        "version" to StringValue(value.version.value), "digest" to StringValue(value.digest.value))
    fun decodePin(value: ObjectValue): ProviderPin {
        value.exact("provider", "version", "digest")
        return ProviderPin(ProviderId.parseOrThrow(value.requiredString("provider")), ProviderVersion.parseOrThrow(value.requiredString("version")),
            ProviderDigest.parseOrThrow(value.requiredString("digest")))
    }
    fun profile(value: EffectiveProfile): ObjectValue = obj("protocol" to StringValue("taskctl.effective-profile/1"),
        "identity" to StringValue(value.identity.value), "bindings" to ObjectValue(value.bindings.entries.sortedBy { it.key.value }.associate { it.key.value to pin(it.value) }))
    fun decodeProfile(value: ObjectValue): EffectiveProfile {
        value.exact("protocol", "identity", "bindings")
        require(value.requiredString("protocol") == "taskctl.effective-profile/1") { "unsupported effective profile" }
        return EffectiveProfile(ProfileId.parseOrThrow(value.requiredString("identity")), value.objectAt("bindings").fields.entries.associate {
            ExtensionId.parseOrThrow(it.key) to decodePin(it.value as? ObjectValue ?: error("provider pin object required"))
        })
    }
    fun audit(value: ProfileAudit): ObjectValue = obj("protocol" to StringValue("taskctl.profile-audit/1"),
        "classification" to StringValue("actor-assertion"), "actor" to StringValue(value.actor), "occurred_at" to StringValue(value.occurredAt.value),
        "reason" to StringValue(value.reason), "evidence" to stringMap(value.evidence))
    fun decodeAudit(value: ObjectValue): ProfileAudit {
        value.exact("protocol", "classification", "actor", "occurred_at", "reason", "evidence")
        require(value.requiredString("protocol") == "taskctl.profile-audit/1" && value.requiredString("classification") == "actor-assertion") { "unsupported profile audit" }
        return ProfileAudit(value.requiredString("actor"), OccurredAt.parseOrThrow(value.requiredString("occurred_at")), value.requiredString("reason"),
            value.objectAt("evidence").fields.mapValues { (it.value as? StringValue)?.value ?: error("profile evidence strings required") })
    }
    fun change(value: ProfileChange): ObjectValue = obj("protocol" to StringValue("taskctl.profile-change/1"),
        "reviewed_head" to optionalString(value.reviewedHead?.value), "profile" to profile(value.profile), "audit" to audit(value.audit))
    fun decodeChange(value: ObjectValue): ProfileChange {
        value.exact("protocol", "reviewed_head", "profile", "audit")
        require(value.requiredString("protocol") == "taskctl.profile-change/1") { "unsupported profile change" }
        return ProfileChange(value.nullableText("reviewed_head")?.let(ProfileRevisionId::parseOrThrow), decodeProfile(value.objectAt("profile")), decodeAudit(value.objectAt("audit")))
    }
    fun revision(value: ProfileRevision): ObjectValue = obj("protocol" to StringValue("taskctl.profile-revision/1"),
        "parent" to optionalString(value.parent?.value), "ledger_revision" to StringValue(value.ledger.value), "profile" to profile(value.profile), "audit" to audit(value.audit))
    fun decodeRevision(value: ObjectValue): ProfileRevision {
        value.exact("protocol", "parent", "ledger_revision", "profile", "audit")
        require(value.requiredString("protocol") == "taskctl.profile-revision/1") { "unsupported profile revision" }
        return ProfileRevision(value.nullableText("parent")?.let(ProfileRevisionId::parseOrThrow), Revision.parseOrThrow(value.requiredString("ledger_revision")),
            decodeProfile(value.objectAt("profile")), decodeAudit(value.objectAt("audit")))
    }
    fun heads(value: ProfileHistory): ObjectValue = obj("protocol" to StringValue("taskctl.profile-history/1"),
        "origin_ledger_revision" to StringValue(value.origin.value), "head" to StringValue(value.head.value))
    fun decodeHeads(value: ObjectValue, revisions: Map<ProfileRevisionId, ProfileRevision>): ProfileHistory {
        value.exact("protocol", "origin_ledger_revision", "head")
        require(value.requiredString("protocol") == "taskctl.profile-history/1") { "unsupported profile history" }
        return ProfileHistory(Revision.parseOrThrow(value.requiredString("origin_ledger_revision")), ProfileRevisionId.parseOrThrow(value.requiredString("head")), revisions).also { it.validate() }
    }
    fun semantics(value: RevisionSemantics): ObjectValue = obj("protocol" to StringValue("taskctl.revision-semantics/1"), "profile" to profile(value.profile),
        "contributed_prerequisites" to strings(value.contributedPrerequisites.sorted().map { it.value }))
    fun decodeSemantics(value: ObjectValue): RevisionSemantics {
        value.exact("protocol", "profile", "contributed_prerequisites")
        require(value.requiredString("protocol") == "taskctl.revision-semantics/1") { "unsupported revision semantics" }
        return RevisionSemantics(decodeProfile(value.objectAt("profile")), value.requiredArray("contributed_prerequisites").map {
            TaskId.parseOrThrow((it as? StringValue)?.value ?: error("contributed prerequisite identity required"))
        })
    }
}
