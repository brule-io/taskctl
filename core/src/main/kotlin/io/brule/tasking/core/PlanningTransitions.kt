package io.brule.tasking.core

/** Planning changes contribute no task DAG edges, lifecycle changes or closure proof. */
internal object PlanningTransitions {
    fun amend(snapshot: LedgerSnapshot, value: PlanningAmendment): DraftUniverse {
        val previous = checkedHead(snapshot, value.record.id, value.reviewedHead).record
        require(value.record.protocol == PlanningRecordCodec.AUDITED_PROTOCOL) { "amendments require the audited planning record contract" }
        require(previous.disposition == value.record.disposition) { "use explicit archive or restore to change planning disposition" }
        require(previous != value.record) { "planning amendment makes no change" }
        return replace(snapshot.universe, value.record)
    }
    fun disposition(snapshot: LedgerSnapshot, value: PlanningDispositionChange): DraftUniverse {
        val previous = checkedHead(snapshot, value.planning, value.reviewedHead).record
        require(previous.disposition != value.disposition) { "planning disposition already selected" }
        return replace(snapshot.universe, previous.withDisposition(value.disposition))
    }
    private fun checkedHead(snapshot: LedgerSnapshot, id: PlanningId, head: PlanningRevisionId): PlanningRevision {
        val history = requireNotNull(snapshot.planningHistory) { "track planning history before amending or assessing" }
        require(history.heads[id] == head) { "planning assertion does not address the current planning HEAD" }
        return history.head(id)
    }
    private fun replace(universe: DraftUniverse, record: PlanningRecord): DraftUniverse = when (record) {
        is DraftRoadmap -> universe.copy(roadmaps = universe.roadmaps.map { if (it.id == record.id) record else it })
        is DraftEpic -> universe.copy(epics = universe.epics.map { if (it.id == record.id) record else it })
    }
    fun validateAssessment(snapshot: LedgerSnapshot, value: PlanningAssessment) {
        val record = checkedHead(snapshot, value.planning, value.reviewedHead).record
        val history = requireNotNull(snapshot.planningHistory)
        require(history.assessmentHeads[value.planning] == value.parent) { "assessment must supersede the current assessment HEAD" }
        require(value.observations.sortedBy { it.task } == snapshot.planningObservations(record)) { "assessment observations do not match current member revisions/contracts/inputs" }
        require(value.criterionEvidence.isEmpty() || value.criterionEvidence.size == record.acceptance.size) { "criterion evidence must address the whole ordered criterion list or be absent" }
        if (value.outcome == PlanningAssessmentOutcome.ACCEPTED) {
            require(record.protocol == PlanningRecordCodec.AUDITED_PROTOCOL && record.acceptance.isNotEmpty()) { "acceptance requires explicit planning criteria" }
            require(value.criterionEvidence.size == record.acceptance.size) { "acceptance requires evidence for every criterion" }
            require((snapshot.universe.tasks.flatMap { it.requiredExtensions } + snapshot.universe.planningRecords.flatMap { it.requiredExtensions }).isEmpty()) {
                "required semantic provider unavailable for planning acceptance"
            }
            val currency = snapshot.currency()
            require(record.tasks.all { currency.getValue(it).state == Currency.CURRENT }) { "member task currency must be current before planning acceptance" }
        }
    }
    fun evolve(before: LedgerSnapshot, after: LedgerSnapshot, transition: Transition): PlanningHistory? {
        if (transition == Transition.TrackPlanning) {
            var adopted = PlanningHistory(before.revision)
            before.universe.planningRecords.sortedBy { it.id.value }.forEach { record ->
                adopted = adopted.append(PlanningRevision(null, record, PlanningChange.Baseline(before.revision)))
            }
            return adopted
        }
        var history = before.planningHistory ?: return null
        when (transition) {
            is Transition.AddRecords -> {
                val records: List<PlanningRecord> = transition.roadmaps + transition.epics
                records.sortedBy { it.id.value }.forEach { record -> history = history.append(PlanningRevision(null, record, PlanningChange.Seeded(before.revision))) }
            }
            is Transition.ImportRecords -> after.universe.planningRecords.sortedBy { it.id.value }.forEach { record ->
                history = history.append(PlanningRevision(null, record, PlanningChange.Imported(transition.admission.manifest.id)))
            }
            is Transition.AmendPlanning -> history = history.append(PlanningRevision(transition.amendment.reviewedHead, transition.amendment.record, PlanningChange.Amended(transition.amendment.audit)))
            is Transition.SetPlanningDisposition -> {
                val change = transition.change
                history = history.append(PlanningRevision(change.reviewedHead, after.universe.planning(change.planning), PlanningChange.DispositionChanged(change.audit)))
            }
            is Transition.AssessPlanning -> history = history.append(transition.assessment)
            is Transition.CloseTask, is Transition.ReviseTask, is Transition.ReconcileTask, Transition.TrackHistory -> Unit
            Transition.TrackPlanning -> error("handled above")
        }
        return history
    }
}
