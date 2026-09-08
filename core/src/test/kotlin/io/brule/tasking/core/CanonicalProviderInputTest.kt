package io.brule.tasking.core

import java.math.BigDecimal
import kotlin.test.*

class CanonicalProviderInputTest {
    @Test fun `equivalent contracts supply identical provider decisions and evidence inputs`() {
        val feature = ExtensionId.parseOrThrow("conformance.canonical-input/v1")
        val pin = ProviderPin(ProviderId.parseOrThrow("conformance.canonical-input-provider/v1"), ProviderVersion.parseOrThrow("1.0.0"),
            ProviderDigest.parseOrThrow("sha256:" + "e".repeat(64)))
        val profile = EffectiveProfile(ProfileId.parseOrThrow("conformance.canonical-input-profile/v1"), mapOf(feature to pin))
        val supplied = mutableListOf<ProviderTask>()
        val provider = object : PinnedSemanticProvider {
            override val feature = feature
            override val pin = pin
            override fun evaluate(record: ProviderTask): Contribution {
                supplied += record
                val payload = record.payload as? ObjectValue ?: error("fixture object required")
                val amount = payload.fields.getValue("amount") as? DecimalValue ?: error("fixture decimal required")
                return Contribution(blockers = if (payload.fields.keys.first() == "amount" && amount.value.scale() == 1)
                    emptyList() else listOf("representation-dependent rejection"))
            }
            override fun verify(record: ProviderTask, evidence: Map<String, String>): List<String> {
                supplied += record
                return if (Json.encode(record.core).contains("0.10")) listOf("representation-dependent evidence rejection") else emptyList()
            }
        }
        val a = obj("amount" to DecimalValue(BigDecimal("0.1")), "nested" to ArrayValue(listOf(obj("a" to DecimalValue(BigDecimal("1E+3")), "z" to NullValue))))
        val b = obj("nested" to ArrayValue(listOf(obj("z" to NullValue, "a" to DecimalValue(BigDecimal("1000.00"))))), "amount" to DecimalValue(BigDecimal("0.10")))
        fun task(payload: ObjectValue) = DraftRecord(TaskId.parseOrThrow("TASK.canonical-input"), "Canonical provider input", "open", "Preserve semantic equivalence",
            emptyList(), listOf("Use the same semantic contract"), listOf("Observe equivalent decisions"), listOf(feature.value), obj(feature.value to payload),
            "tasking/core-draft-2", listOf("test"))
        val first = task(a); val second = task(b)
        val rawFirst = Json.encode(NativeCodec.task(first)); val rawSecond = Json.encode(NativeCodec.task(second))
        assertNotEquals(rawFirst, rawSecond)
        assertEquals(effectiveContract(first, profile), effectiveContract(second, profile))
        val resolved = ProviderRegistry(listOf(provider)).resolved(profile).single()
        assertEquals(resolved.evaluate(first), resolved.evaluate(second))
        assertEquals(resolved.verify(first, emptyMap()), resolved.verify(second, emptyMap()))
        assertEquals(1, supplied.map { Json.encode(it.payload) }.distinct().size)
        assertEquals(1, supplied.map { Json.encode(it.core) }.distinct().size)
        assertEquals(rawFirst, Json.encode(NativeCodec.task(first)))
        assertEquals(rawSecond, Json.encode(NativeCodec.task(second)))
    }
}
