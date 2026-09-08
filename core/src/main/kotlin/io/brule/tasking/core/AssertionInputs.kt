package io.brule.tasking.core

import java.util.Collections

/** Programmatic callers cross the same typed assertion boundary as decoded
 * commands. Read-only DTO collections may still alias mutable caller inputs.
 * Reuse the existing codecs so old assertion/time protocols keep their meaning. */
internal fun validateAssertionInput(transition: Transition) {
    when (transition) {
        is Transition.CloseTask -> NativeCodec.decodeEvidence(NativeCodec.evidence(transition.evidence))
        is Transition.ReconcileTask -> HistoryCodec.decodeReview(HistoryCodec.review(transition.review))
        is Transition.AmendPlanning -> PlanningHistoryCodec.decodeAmendment(PlanningHistoryCodec.amendment(transition.amendment))
        is Transition.SetPlanningDisposition -> PlanningHistoryCodec.decodeDisposition(PlanningHistoryCodec.disposition(transition.change))
        is Transition.AssessPlanning -> PlanningHistoryCodec.decodeAssessment(PlanningHistoryCodec.assessment(transition.assessment))
        is Transition.SetProfile -> ProfileCodec.decodeChange(ProfileCodec.change(transition.change))
        // Record constructors and reviewed import identities have their own
        // shared reducer checks. Baseline adoption introduces no actor assertion.
        is Transition.AddRecords, is Transition.ReviseTask, is Transition.ImportRecords,
        Transition.TrackHistory, Transition.TrackPlanning -> Unit
    }
}

/** A provider can inspect evidence or reject it, never edit the caller's claim. */
internal fun evidenceView(evidence: Map<String, String>): Map<String, String> =
    Collections.unmodifiableMap(LinkedHashMap(evidence))
