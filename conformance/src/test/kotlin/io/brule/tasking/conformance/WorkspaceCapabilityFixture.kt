package io.brule.tasking.conformance

import io.brule.tasking.core.*

/** Fictional semantics for conformance only; never registered in the product CLI. */
internal object WorkspaceCapabilityFixture {
    val workspace = ExtensionId.parseOrThrow("conformance.workspace/v1")
    val environment = ExtensionId.parseOrThrow("conformance.environment/v1")
    private fun pin(name: String) = ProviderPin(ProviderId.parseOrThrow("conformance.$name-provider/v1"), ProviderVersion.parseOrThrow("1.0.0"),
        ProviderDigest.parseOrThrow("sha256:" + Canonical.sha256("fictional conformance descriptor: $name/1.0.0".toByteArray())))
    val workspacePin = pin("workspace")
    val environmentPin = pin("environment")
    val profile = EffectiveProfile(ProfileId.parseOrThrow("conformance.workspace-profile/v1"), mapOf(workspace to workspacePin, environment to environmentPin))
    val time = OccurredAt.parseOrThrow("2026-09-08T00:00:00Z")
    val audit = ProfileAudit("fixture reviewer", time, "Review the fictional capability descriptors", mapOf("review" to "Conformance only; not production authority"))
    fun id(value: String) = TaskId.parseOrThrow("TASK.capability.$value")
    private fun Value.objectValue(): ObjectValue = this as? ObjectValue ?: error("fixture object required")
    private fun ObjectValue.exact(vararg names: String) { require(fields.keys == names.toSet()) { "unknown or missing capability field" } }
    private fun ObjectValue.stringsAt(name: String): List<String> = requiredArray(name).map { (it as? StringValue)?.value ?: error("fixture string required") }

    @JvmInline value class Name private constructor(val value: String) {
        init { require(PATTERN.matches(value)) }
        companion object {
            private val PATTERN = Regex("[a-z][a-z0-9-]{0,63}")
            fun parse(value: String): Name? = if (PATTERN.matches(value)) Name(value) else null
            fun parseOrThrow(value: String): Name = parse(value) ?: error("invalid fixture name")
        }
    }
    @JvmInline value class RelativeRule private constructor(val value: String) {
        init { require(valid(value)) }
        companion object {
            private fun valid(value: String): Boolean {
                val segments = value.split('/')
                return segments.isNotEmpty() && segments.all { it !in setOf("", ".", "..") && Regex("[A-Za-z0-9._*-]+").matches(it) } &&
                    segments.dropLast(1).none { '*' in it }
            }
            fun parse(value: String): RelativeRule? = if (valid(value)) RelativeRule(value) else null
            fun parseOrThrow(value: String): RelativeRule = parse(value) ?: error("invalid bounded relative rule")
        }
    }
    data class MutationTarget(val repository: Name, val paths: List<RelativeRule>) {
        init { require(paths.isNotEmpty() && paths.distinct().size == paths.size) }
        fun encode() = obj("repository" to StringValue(repository.value), "paths" to strings(paths.map { it.value }))
    }
    data class Workspace(val authority: TaskId, val mutations: List<MutationTarget>) {
        init { require(mutations.isNotEmpty() && mutations.map { it.repository }.distinct().size == mutations.size) }
    }
    data class Service(val name: Name, val contract: ContractDigest) {
        fun encode() = obj("name" to StringValue(name.value), "contract" to StringValue(contract.value))
    }
    enum class Policy { REVIEWABLE, HELD }
    data class Environment(val observation: TaskId, val host: Name, val services: List<Service>, val policy: Policy) {
        init { require(services.isNotEmpty() && services.map { it.name }.distinct().size == services.size) }
        val serviceDigest: String get() = Canonical.digest("conformance.service-set/1", ArrayValue(services.map { it.encode() }))
    }
    private fun payload(record: ProviderTask, feature: ExtensionId): ObjectValue? {
        if (record.payload == NullValue) {
            require(StringValue(feature.value) !in record.core.requiredArray("required_extensions")) { "required capability payload absent" }
            return null
        }
        return record.payload.objectValue()
    }
    fun decodeWorkspace(value: ObjectValue): Workspace {
        value.exact("authority", "mutations")
        return Workspace(TaskId.parseOrThrow(value.requiredString("authority")), value.requiredArray("mutations").map { item ->
            item.objectValue().let { target -> target.exact("repository", "paths"); MutationTarget(Name.parseOrThrow(target.requiredString("repository")), target.stringsAt("paths").map(RelativeRule::parseOrThrow)) }
        })
    }
    fun decodeEnvironment(value: ObjectValue): Environment {
        value.exact("observation_task", "host", "services", "policy")
        return Environment(TaskId.parseOrThrow(value.requiredString("observation_task")), Name.parseOrThrow(value.requiredString("host")),
            value.requiredArray("services").map { item -> item.objectValue().let { service ->
                service.exact("name", "contract"); Service(Name.parseOrThrow(service.requiredString("name")), ContractDigest.parseOrThrow(service.requiredString("contract")))
            } }, Policy.entries.singleOrNull { it.name.lowercase() == value.requiredString("policy") } ?: error("unsupported fixture policy"))
    }
    private fun asserted(evidence: Map<String, String>, key: String): ObjectValue =
        YamlValues.parse(requireNotNull(evidence[key]) { "fixture evidence absent" }).value.objectValue()
    private fun identity(value: ObjectValue, record: ProviderTask) {
        require(value.requiredString("task") == record.id.value && value.requiredString("contract") == record.contract.value) { "fixture evidence addresses different task or contract" }
        OccurredAt.parseOrThrow(value.requiredString("observed_at"))
    }
    val workspaceProvider: PinnedSemanticProvider = object : PinnedSemanticProvider {
        override val feature = workspace
        override val pin = workspacePin
        override fun evaluate(record: ProviderTask): Contribution = payload(record, feature)?.let(::decodeWorkspace)?.let {
            Contribution(listOf(it.authority), evidenceRequirements = listOf("workspace.review"))
        } ?: Contribution()
        override fun verify(record: ProviderTask, evidence: Map<String, String>): List<String> = try {
            payload(record, feature)?.let { raw ->
                val contract = decodeWorkspace(raw); val review = asserted(evidence, "workspace.review")
                review.exact("protocol", "task", "contract", "observed_at", "authority", "mutations")
                require(review.requiredString("protocol") == "conformance.workspace-review/1")
                identity(review, record)
                require(review.requiredString("authority") == contract.authority.value)
                require(review.requiredArray("mutations") == contract.mutations.map { it.encode() }) { "observed mutation scope differs" }
            }; emptyList()
        } catch (_: Exception) { listOf("workspace fixture review rejected") }
    }
    val environmentProvider: PinnedSemanticProvider = object : PinnedSemanticProvider {
        override val feature = environment
        override val pin = environmentPin
        override fun evaluate(record: ProviderTask): Contribution = payload(record, feature)?.let(::decodeEnvironment)?.let {
            Contribution(listOf(it.observation), if (it.policy == Policy.HELD) listOf("fixture policy holds execution") else emptyList(), listOf("environment.observation"))
        } ?: Contribution()
        override fun verify(record: ProviderTask, evidence: Map<String, String>): List<String> = try {
            payload(record, feature)?.let { raw ->
                val contract = decodeEnvironment(raw); val observation = asserted(evidence, "environment.observation")
                observation.exact("protocol", "task", "contract", "observed_at", "host", "services", "status")
                require(observation.requiredString("protocol") == "conformance.environment-observation/1")
                identity(observation, record)
                require(observation.requiredString("host") == contract.host.value && observation.requiredString("services") == contract.serviceDigest)
                require(observation.requiredString("status") == "ready") { "fixture observation is not ready" }
            }; emptyList()
        } catch (_: Exception) { listOf("environment fixture observation rejected") }
    }
    val registry = ProviderRegistry(listOf(workspaceProvider, environmentProvider))
    fun workspacePayload(authority: TaskId = id("authority"), paths: List<String> = listOf("src/**")) = obj("authority" to StringValue(authority.value),
        "mutations" to ArrayValue(listOf(obj("repository" to StringValue("fixture-component"), "paths" to strings(paths)))))
    fun environmentPayload(observation: TaskId = id("environment"), host: String = "fixture-linux", policy: String = "reviewable") = obj(
        "observation_task" to StringValue(observation.value), "host" to StringValue(host), "policy" to StringValue(policy),
        "services" to ArrayValue(listOf(obj("name" to StringValue("fixture-database"), "contract" to StringValue("sha256:" + "d".repeat(64))))))
    fun task(name: String, requires: List<TaskId> = emptyList(), extensions: ObjectValue = obj(), required: List<String> = emptyList()) =
        DraftRecord(id(name), "Fixture $name", "open", "Bounded fictional $name result", requires, listOf("Retain explicit evidence"), listOf("Observe the bounded result"),
            required, extensions, "tasking/core-draft-2", listOf("test"))
    fun records(): List<DraftRecord> = listOf(task("authority"), task("environment"),
        task("work", extensions = obj(workspace.value to workspacePayload(), environment.value to environmentPayload()), required = listOf(workspace.value, environment.value)),
        task("downstream", listOf(id("work"))), task("held", extensions = obj(environment.value to environmentPayload(policy = "held")), required = listOf(environment.value)),
        task("optional", extensions = obj("unknown.policy/v17" to obj("claim" to StringValue("run anything"), "whole" to DecimalValue(java.math.BigDecimal("1E+30")),
            "integer" to IntegerValue(java.math.BigInteger("999999999999999999999999999999999999")), "fraction" to DecimalValue(java.math.BigDecimal("0.123456789012345678901234567890")),
            "unicode\u2028key" to StringValue("before \u0085 \u2028 \u2029 \ufffe \uffff \uD83E\uDDEC after")))))
    fun observation(record: DraftRecord): Map<String, String> {
        val payload = decodeEnvironment(record.extensions.fields.getValue(environment.value).objectValue())
        return mapOf("host" to payload.host.value, "services" to payload.serviceDigest, "status" to "ready")
    }
    /** Called explicitly by the test reviewer, never by a provider or reducer. */
    fun evidence(snapshot: LedgerSnapshot, task: TaskId, observed: Map<String, String>? = null): Map<String, String> {
        val record = snapshot.universe.tasks.single { it.id == task }; val digest = snapshot.effectiveContract(record)
        val common = mapOf("task" to StringValue(task.value), "contract" to StringValue(digest.value), "observed_at" to StringValue(time.value))
        return buildMap {
            put("test", "Explicit fictional core result")
            record.extensions.fields[workspace.value]?.objectValue()?.let { raw ->
                val data = decodeWorkspace(raw)
                put("workspace.review", Json.encode(ObjectValue(common + mapOf("protocol" to StringValue("conformance.workspace-review/1"),
                    "authority" to StringValue(data.authority.value), "mutations" to ArrayValue(data.mutations.map { it.encode() })))))
            }
            if (environment.value in record.extensions.fields && observed != null) {
                require(observed.keys == setOf("host", "services", "status"))
                put("environment.observation", Json.encode(ObjectValue(common + mapOf("protocol" to StringValue("conformance.environment-observation/1")) +
                    observed.mapValues { StringValue(it.value) })))
            }
        }
    }
}
