package io.brule.tasking.cli

import io.brule.tasking.core.*
import io.brule.tasking.repository.FileTaskLedger
import java.nio.file.Files
import java.nio.file.Path

internal object PlanningCommands {
    fun run(args: NativeCommands.Arguments): ObjectValue {
        val command = args.positional.firstOrNull() ?: error("planning track|amend|archive|restore|assess|history")
        when (command) {
            "track" -> args.allow("--expect-revision", "--plan", positions = 1)
            "amend", "archive", "restore", "assess" -> args.allow("--file", "--expect-revision", "--plan", positions = 2)
            "history" -> args.allow(positions = 2)
            else -> error("unknown planning command: $command")
        }
        val ledger = FileTaskLedger(args.root())
        fun mutate(transition: Transition): ObjectValue {
            val expected = Revision.parseOrThrow(args.need("--expect-revision"))
            if ("--plan" in args.options) return ledger.plan(expected, transition)
            val result = ledger.apply(expected, transition)
            return obj("revision" to StringValue(result.revision.value), "changed" to strings(result.changed.map { it.value }))
        }
        if (command == "track") return mutate(Transition.TrackPlanning)
        val id = PlanningId.parseOrThrow(args.positional[1])
        if (command == "history") return PlanningReadModels.history(ledger.snapshot(), id)
        if (command == "assess" && "--file" !in args.options) {
            require("--plan" in args.options && "--expect-revision" !in args.options) { "assess requires --file and --expect-revision, or --plan alone to inspect inputs" }
            return PlanningReadModels.assessmentPlan(ledger.snapshot(), id)
        }
        val input = YamlValues.parse(Files.readString(Path.of(args.need("--file")))).value as? ObjectValue ?: error("planning input must be an object")
        return when (command) {
            "amend" -> {
                val value = PlanningHistoryCodec.decodeAmendment(input)
                require(value.record.id == id) { "amendment addresses a different planning identity" }
                mutate(Transition.AmendPlanning(value))
            }
            "archive", "restore" -> {
                val value = PlanningHistoryCodec.decodeDisposition(input)
                require(value.planning == id) { "disposition assertion addresses a different planning identity" }
                require(value.disposition == if (command == "archive") PlanningDisposition.ARCHIVED else PlanningDisposition.ACTIVE) { "disposition disagrees with requested command" }
                mutate(Transition.SetPlanningDisposition(value))
            }
            "assess" -> {
                val value = PlanningHistoryCodec.decodeAssessment(input)
                require(value.planning == id) { "assessment addresses a different planning identity" }
                mutate(Transition.AssessPlanning(value))
            }
            else -> error("planning command already handled")
        }
    }
}
