package io.brule.tasking.cli

import io.brule.tasking.core.*
import io.brule.tasking.repository.FileTaskLedger
import java.nio.file.Files
import java.nio.file.Path

internal object ProfileCommands {
    fun run(args: NativeCommands.Arguments): ObjectValue {
        val command = args.positional.firstOrNull() ?: error("profile show|history|set")
        when (command) {
            "show", "history" -> args.allow(positions = 1)
            "set" -> args.allow("--file", "--expect-revision", "--plan", positions = 1)
            else -> error("unknown profile command: $command")
        }
        val ledger = FileTaskLedger(args.root())
        if (command != "set") return ProfileReadModels.view(ledger.snapshot(), command == "history")
        val input = YamlValues.parse(Files.readString(Path.of(args.need("--file")))).value as? ObjectValue ?: error("profile input must be an object")
        val transition = Transition.SetProfile(ProfileCodec.decodeChange(input))
        val revision = Revision.parseOrThrow(args.need("--expect-revision"))
        if ("--plan" in args.options) return ledger.plan(revision, transition)
        val result = ledger.apply(revision, transition)
        return obj("revision" to StringValue(result.revision.value), "changed" to strings(result.changed.map { it.value }))
    }
}

internal object ProfileReadModels {
    fun complete(value: ProfileHistory): ObjectValue = obj("heads" to ProfileCodec.heads(value),
        "revisions" to ObjectValue(value.revisions.entries.sortedBy { it.key.value }.associate { it.key.value to ProfileCodec.revision(it.value) }))
    fun view(snapshot: LedgerSnapshot, history: Boolean): ObjectValue {
        val current = snapshot.profileHistory
        val result = obj("contract" to StringValue(if (history) "taskctl.profile-history-view/1" else "taskctl.profile-view/1"),
            "revision" to StringValue(snapshot.revision.value), "tracked" to BooleanValue(current != null),
            "head" to optionalString(current?.head?.value), "profile_digest" to optionalString(current?.profile?.digest?.value),
            "profile" to (current?.profile?.let(ProfileCodec::profile) ?: NullValue),
            "semantic_problems" to strings(snapshot.semanticProblems()),
            "instructions" to StringValue("Profile changes require taskctl.profile-change/1, the exact reviewed profile HEAD (null before adoption), full profile and an explicit profile audit. Historical revisions keep their original profile. The CLI has no runtime provider loader; unavailable pins block execution."))
        return if (!history) result else ObjectValue(result.fields + ("history" to (current?.let(::complete) ?: NullValue)))
    }
}
