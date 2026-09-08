package io.brule.tasking.cli

import io.brule.tasking.core.*
import kotlin.test.*

class ProfileReadModelsTest {
    private val task = DraftRecord(TaskId.parseOrThrow("TASK.a"), "A", "open", "Durable", emptyList(), listOf("Persist"), listOf("Restart"), emptyList(), obj(), "tasking/core-draft-2", listOf("unit"))
    private val audit = ProfileAudit("reviewer", OccurredAt.parseOrThrow("2026-09-08T00:00:00Z"), "Reviewed profile", mapOf("review" to "Bounded proof"))
    private fun legacy() = LedgerTransitions.evolve(LedgerSnapshot("test", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial())), Transition.AddRecords(listOf(task)))

    @Test fun `profile history view is complete while context remains bounded and legacy output stays versioned`() {
        val old = legacy()
        assertEquals(BooleanValue(false), ProfileReadModels.view(old, false).fields["tracked"])
        assertEquals("taskctl.doctor/alpha1", LedgerReadModels.doctor(old).requiredString("contract"))
        val profile = EffectiveProfile(ProfileId.parseOrThrow("test.empty/v1"), emptyMap())
        val changed = LedgerTransitions.evolve(old, Transition.SetProfile(ProfileChange(null, profile, audit)))
        val snapshot = LedgerReadModels.snapshot(changed)
        assertEquals("taskctl.snapshot/alpha3", snapshot.requiredString("contract"))
        assertEquals("taskctl.native/alpha5", snapshot.requiredString("protocol"))
        assertEquals(profile.digest.value, snapshot.requiredString("profile"))
        assertEquals(ProfileReadModels.complete(changed.profileHistory!!), snapshot.fields["profile_history"])
        assertEquals(snapshot, YamlValues.parse(Json.encode(snapshot)).value)
        val context = LedgerReadModels.context(changed)
        assertEquals(profile.digest.value, context.requiredString("profile"))
        assertTrue(Json.encode(context).toByteArray().size <= LedgerReadModels.CONTEXT_MAX_BYTES)
        val doctor = LedgerReadModels.doctor(changed)
        assertEquals("taskctl.doctor/alpha3", doctor.requiredString("contract"))
        assertEquals("attention", doctor.requiredString("health"))
        assertEquals(old.history, changed.history)
    }

    @Test fun `missing effective provider is visible and cannot supply a frontier or an acceptance claim`() {
        val feature = ExtensionId.parseOrThrow("test.execution/v1")
        val pin = ProviderPin(ProviderId.parseOrThrow("test.executor/v1"), ProviderVersion.parseOrThrow("1.0.0"), ProviderDigest.parseOrThrow("sha256:" + "d".repeat(64)))
        val profile = EffectiveProfile(ProfileId.parseOrThrow("test.workspace/v1"), mapOf(feature to pin))
        val changed = LedgerTransitions.evolve(legacy(), Transition.SetProfile(ProfileChange(null, profile, audit)))
        val doctor = LedgerReadModels.doctor(changed)
        assertEquals("blocked", doctor.requiredString("health"))
        assertEquals(strings(listOf(feature.value)), doctor.fields["required_capabilities_unavailable"])
        assertTrue(ProfileReadModels.view(changed, false).requiredArray("semantic_problems").isNotEmpty())
        val snapshot = LedgerReadModels.snapshot(changed)
        assertEquals(strings(emptyList()), (snapshot.fields.getValue("derived") as ObjectValue).fields["frontier"])
        val context = LedgerReadModels.context(changed)
        assertEquals(integer(0), (context.fields.getValue("counts") as ObjectValue).fields["ready"])
        assertEquals(profile.digest.value, context.requiredString("profile"))
    }
}
