package io.brule.workflow.task

import io.brule.workflow.diagnostics.Diagnostic
import io.brule.workflow.diagnostics.ValidationReport
import io.brule.workflow.document.FrontMatterDocument
import io.brule.workflow.document.FrontMatterParser
import io.brule.workflow.document.MarkdownDocument
import io.brule.workflow.document.MarkdownParser
import io.brule.workflow.document.TypedFields
import io.brule.workflow.io.RepositoryLayout
import io.brule.workflow.io.withWorkflowRead
import io.brule.workflow.model.EpicRef
import io.brule.workflow.model.RoadmapRef
import io.brule.workflow.model.TaskRef
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

public data class TaskLedgerSnapshot(
    public val tasks: List<TaskDocument>,
    public val roadmaps: List<RoadmapDocument>,
    public val epics: List<EpicDocument>,
    public val report: ValidationReport,
) {
    public val openTasks: List<TaskDocument> =
        tasks.filter { it.state == LedgerState.OPEN }.sortedWith(
            TaskDocument.ORDER,
        )
    public val closedTasks: List<TaskDocument> = tasks.filter { it.state == LedgerState.CLOSED }.sortedBy { it.ref }
    public val graph: TaskGraphAnalysis = TaskGraph.analyze(openTasks)

    public fun task(ref: TaskRef): TaskDocument? = tasks.singleOrNull { it.ref == ref }

    public fun roadmap(ref: RoadmapRef): RoadmapDocument? = roadmaps.singleOrNull { it.ref == ref }

    public fun derivedBlocks(ref: TaskRef): List<TaskRef> = tasks.filter { ref in it.depends }.map { it.ref }.sorted()

    public fun derivedRealizations(ref: TaskRef): List<TaskRef> =
        tasks.filter { it.realizes == ref }.map { it.ref }.sorted()
}

public class TaskLedger(
    private val layout: RepositoryLayout,
) {
    public fun load(): TaskLedgerSnapshot = withWorkflowRead(layout) { loadUnlocked() }

    public fun inspect(): Pair<TaskLedgerSnapshot, String> = withWorkflowRead(layout) {
        loadUnlocked() to RecordSnapshot.digest(layout)
    }

    internal fun loadUnlocked(ignoredPendingClosureJournal: Path? = null): TaskLedgerSnapshot {
        val diagnostics = mutableListOf<Diagnostic>()
        diagnostics += pendingClosureDiagnostics(ignoredPendingClosureJournal)
        val tasks =
            buildList {
                addAll(loadTasks(layout.openTasksDirectory, LedgerState.OPEN, diagnostics))
                addAll(loadTasks(layout.closedTasksDirectory, LedgerState.CLOSED, diagnostics))
            }
        val roadmaps =
            buildList {
                addAll(loadRoadmaps(layout.openRoadmapsDirectory, LedgerState.OPEN, diagnostics))
                addAll(loadRoadmaps(layout.closedRoadmapsDirectory, LedgerState.CLOSED, diagnostics))
            }
        val epics =
            buildList {
                addAll(loadEpics(layout.openEpicsDirectory, LedgerState.OPEN, diagnostics))
                addAll(loadEpics(layout.closedEpicsDirectory, LedgerState.CLOSED, diagnostics))
        }
        validateTaskRelations(tasks, roadmaps, epics, diagnostics)
        diagnostics += TaskGraph.diagnostics(tasks)
        return TaskLedgerSnapshot(
            tasks.sortedBy { it.ref },
            roadmaps.sortedWith(compareBy<RoadmapDocument>({ it.ordinal }, { it.ref.value })),
            epics.sortedBy { it.ref },
            ValidationReport(diagnostics.sorted()),
        )
    }

    private fun pendingClosureDiagnostics(ignoredJournal: Path?): List<Diagnostic> {
        if (!Files.isDirectory(layout.agentsDirectory)) return emptyList()
        val normalizedIgnored = ignoredJournal?.toAbsolutePath()?.normalize()
        return Files.list(layout.agentsDirectory).use { paths ->
            paths
                .filter { path ->
                    Files.isRegularFile(path) &&
                        path.toAbsolutePath().normalize() != normalizedIgnored &&
                        LedgerClosureTarget.fromJournalPath(path) != null
                }.map { path ->
                    val target = requireNotNull(LedgerClosureTarget.fromJournalPath(path))
                    val code =
                        when (target.kind) {
                            LedgerClosureKind.TASK -> "TASK_CLOSURE_PENDING"
                            LedgerClosureKind.ROADMAP -> "ROADMAP_CLOSURE_PENDING"
                        }
                    Diagnostic(
                        code,
                        "pending closure transaction; rerun ${target.recoveryCommand} with the original receipt " +
                            "and frontier arguments",
                        path,
                    )
                }.toList()
        }
    }

    private fun loadTasks(
        directory: Path,
        state: LedgerState,
        diagnostics: MutableList<Diagnostic>,
    ): List<TaskDocument> = markdownFiles(directory).mapNotNull { path -> decodeTask(path, state, diagnostics) }

    private fun loadRoadmaps(
        directory: Path,
        state: LedgerState,
        diagnostics: MutableList<Diagnostic>,
    ): List<RoadmapDocument> = markdownFiles(directory).mapNotNull { path -> decodeRoadmap(path, state, diagnostics) }

    private fun loadEpics(
        directory: Path,
        state: LedgerState,
        diagnostics: MutableList<Diagnostic>,
    ): List<EpicDocument> = markdownFiles(directory).mapNotNull { path -> decodeEpic(path, state, diagnostics) }

    private fun decodeTask(
        path: Path,
        state: LedgerState,
        diagnostics: MutableList<Diagnostic>,
    ): TaskDocument? {
        val parsed = parseDocument(path, diagnostics) ?: return null
        val fields = TypedFields(parsed.frontMatter, diagnostics)
        fields.rejectUnknown(TASK_FIELDS)
        fields.requireOrder(TASK_FIELD_ORDER, TASK_OPTIONAL_FIELDS)
        val kind = fields.requiredScalar("kind")
        val schema = fields.requiredScalar("schema")
        val rawRef = fields.requiredScalar("ref")
        val rawRoadmap = fields.requiredScalar("roadmap")
        val rawEffort = fields.requiredScalar("effort")
        val rawImpact = fields.requiredScalar("impact")
        val rawDepends = fields.sequence("depends")
        val rawRealizes = fields.optionalScalar("realizes")
        if (kind != null && kind != "task") {
            diagnostics += Diagnostic("TASK_KIND", "kind must be task", path, 1)
        }
        if (schema != null && schema != AGENT_SCHEMA) {
            diagnostics += Diagnostic("TASK_SCHEMA", "schema must be $AGENT_SCHEMA", path, 1)
        }
        val ref = rawRef?.let(TaskRef::parse)
        if (rawRef != null && ref == null) {
            diagnostics += Diagnostic("TASK_REF", "ref must match ${TaskRef.EXPECTED_FORMS}", path, 1)
        }
        val roadmap = rawRoadmap?.let(RoadmapRef::parse)
        if (rawRoadmap != null && roadmap == null) {
            diagnostics += Diagnostic("TASK_ROADMAP_REF", "roadmap must match ROADMAP.<namespace>.<NNN>", path, 1)
        }
        val effort = rawEffort?.let { enumValueOrNull<Effort>(it) }
        if (rawEffort != null && effort == null) {
            diagnostics += Diagnostic("TASK_EFFORT", "effort must be LOW, MEDIUM, or HIGH", path, 1)
        }
        val impact = rawImpact?.let { enumValueOrNull<Impact>(it) }
        if (rawImpact != null && impact == null) {
            diagnostics += Diagnostic("TASK_IMPACT", "impact must be LOW, MEDIUM, or HIGH", path, 1)
        }
        val depends =
            rawDepends.mapNotNull { dependency ->
                TaskRef.parse(dependency).also { parsedRef ->
                    if (parsedRef == null) {
                        diagnostics +=
                            Diagnostic(
                                "TASK_DEPENDENCY_REF",
                                "dependency '$dependency' must match ${TaskRef.EXPECTED_FORMS}",
                                path,
                                1,
                            )
                    }
                }
            }
        if (depends.size != depends.distinct().size) {
            diagnostics += Diagnostic("TASK_DEPENDENCY_DUPLICATE", "depends contains duplicate refs", path, 1)
        }
        if (depends != depends.sorted()) {
            diagnostics += Diagnostic("TASK_DEPENDENCY_ORDER", "depends must be sorted lexically", path, 1)
        }
        val realizes =
            rawRealizes?.let { aggregate ->
                TaskRef.parse(aggregate).also { parsedRef ->
                    if (parsedRef == null) {
                        diagnostics +=
                            Diagnostic(
                                "TASK_REALIZATION_REF",
                                "realizes '$aggregate' must match ${TaskRef.EXPECTED_FORMS}",
                                path,
                                1,
                            )
                    }
                }
            }
        validateRequiredTaskSections(parsed.markdown, path, state, diagnostics)
        if (ref != null) validateTaskIdentity(path, ref, parsed.markdown, diagnostics)

        if (ref == null || roadmap == null || effort == null || impact == null) return null
        return TaskDocument(
            path = path,
            state = state,
            ref = ref,
            roadmap = roadmap,
            effort = effort,
            impact = impact,
            depends = depends.distinct().sorted(),
            realizes = realizes,
            title = parsed.markdown.heading.orEmpty(),
            frontMatter = parsed.frontMatter,
            markdown = parsed.markdown,
        )
    }

    private fun decodeRoadmap(
        path: Path,
        state: LedgerState,
        diagnostics: MutableList<Diagnostic>,
    ): RoadmapDocument? {
        val parsed = parseDocument(path, diagnostics) ?: return null
        val fields = TypedFields(parsed.frontMatter, diagnostics)
        fields.rejectUnknown(ROADMAP_FIELDS)
        fields.requireOrder(ROADMAP_FIELD_ORDER)
        val kind = fields.requiredScalar("kind")
        val schema = fields.requiredScalar("schema")
        val rawRef = fields.requiredScalar("ref")
        val rawEpic = fields.requiredScalar("epic")
        val rawOrdinal = fields.requiredScalar("ordinal")
        if (kind != null && kind != "roadmap") {
            diagnostics += Diagnostic("ROADMAP_KIND", "kind must be roadmap", path, 1)
        }
        if (schema != null && schema != AGENT_SCHEMA) {
            diagnostics += Diagnostic("ROADMAP_SCHEMA", "schema must be $AGENT_SCHEMA", path, 1)
        }
        val ref = rawRef?.let(RoadmapRef::parse)
        if (rawRef != null && ref == null) {
            diagnostics += Diagnostic("ROADMAP_REF", "ref must match ROADMAP.<namespace>.<NNN>", path, 1)
        }
        val epic = rawEpic?.let(EpicRef::parse)
        if (rawEpic != null && epic == null) {
            diagnostics += Diagnostic("ROADMAP_EPIC_REF", "epic must match EPIC.<namespace>.<NNN>", path, 1)
        }
        val ordinal = rawOrdinal?.toIntOrNull()
        if (rawOrdinal != null && (ordinal == null || ordinal < 0)) {
            diagnostics += Diagnostic("ROADMAP_ORDINAL", "ordinal must be a non-negative integer", path, 1)
        }
        validateLifecycleSections(
            parsed.markdown,
            path,
            state,
            ROADMAP_SECTION_ORDER,
            checklistSection = "Exit Criteria",
            diagnostics = diagnostics,
        )
        if (ref != null) validateRoadmapIdentity(path, ref, parsed.markdown, diagnostics)
        if (ref == null || ordinal == null) return null
        return RoadmapDocument(
            path,
            state,
            ref,
            epic,
            ordinal,
            parsed.markdown.heading.orEmpty(),
            parsed.frontMatter,
            parsed.markdown,
        )
    }

    private fun decodeEpic(
        path: Path,
        state: LedgerState,
        diagnostics: MutableList<Diagnostic>,
    ): EpicDocument? {
        val parsed = parseDocument(path, diagnostics) ?: return null
        val fields = TypedFields(parsed.frontMatter, diagnostics)
        fields.rejectUnknown(EPIC_FIELDS)
        fields.requireOrder(EPIC_FIELD_ORDER)
        val kind = fields.requiredScalar("kind")
        val schema = fields.requiredScalar("schema")
        val rawRef = fields.requiredScalar("ref")
        if (kind != null && kind != "epic") {
            diagnostics += Diagnostic("EPIC_KIND", "kind must be epic", path, 1)
        }
        if (schema != null && schema != AGENT_SCHEMA) {
            diagnostics += Diagnostic("EPIC_SCHEMA", "schema must be $AGENT_SCHEMA", path, 1)
        }
        val ref = rawRef?.let(EpicRef::parse)
        if (rawRef != null && ref == null) {
            diagnostics += Diagnostic("EPIC_REF", "ref must match EPIC.<namespace>.<NNN>", path, 1)
        }
        validateLifecycleSections(
            parsed.markdown,
            path,
            state,
            EPIC_SECTION_ORDER,
            checklistSection = null,
            diagnostics = diagnostics,
        )
        if (ref != null) validateEpicIdentity(path, ref, parsed.markdown, diagnostics)
        if (ref == null) return null
        return EpicDocument(
            path,
            state,
            ref,
            parsed.markdown.heading.orEmpty(),
            parsed.frontMatter,
            parsed.markdown,
        )
    }

    private fun parseDocument(
        path: Path,
        diagnostics: MutableList<Diagnostic>,
    ): ParsedMarkdown? {
        val input = Files.readString(path, StandardCharsets.UTF_8)
        val parseResult = FrontMatterParser.parse(path, input)
        diagnostics += parseResult.diagnostics
        val frontMatter = parseResult.document ?: return null
        val markdown = MarkdownParser.parse(path, frontMatter.body, frontMatter.bodyStartLine)
        diagnostics += markdown.diagnostics
        return ParsedMarkdown(frontMatter, markdown)
    }

    private fun validateRequiredTaskSections(
        markdown: MarkdownDocument,
        path: Path,
        state: LedgerState,
        diagnostics: MutableList<Diagnostic>,
    ) {
        validateSectionOrder(markdown, path, TASK_SECTION_ORDER, diagnostics)
        markdown.section("Description")?.let { description ->
            if (description.content.isBlank()) {
                diagnostics +=
                    Diagnostic("TASK_DESCRIPTION_EMPTY", "Description must not be empty", path, description.headingLine)
            }
        }
        markdown.section("Requirements")?.let { requirements ->
            if (requirements.content.lineSequence().none { it.trimStart().startsWith("- ") }) {
                diagnostics +=
                    Diagnostic(
                        "TASK_REQUIREMENTS_EMPTY",
                        "Requirements must contain a bullet",
                        path,
                        requirements.headingLine,
                    )
            }
        }
        val deliverables = markdown.deliverables()
        if (markdown.section("Deliverables") != null && deliverables.isEmpty()) {
            diagnostics += Diagnostic("TASK_DELIVERABLES_EMPTY", "Deliverables must contain a Markdown checkbox", path)
        }
        val closure = markdown.section("Closure")
        if (state == LedgerState.CLOSED && deliverables.any { !it.completed }) {
            diagnostics += Diagnostic("TASK_CLOSED_DELIVERABLE", "closed task has unchecked deliverables", path)
        }
        if (closure != null) {
            val receiptResult = ClosureReceiptParser.parse(path, closure.content)
            diagnostics += receiptResult.diagnostics
            receiptResult.receipt?.let { receipt ->
                diagnostics += ClosureReceiptParser.validate(path, state, receipt)
            }
        }
    }

    private fun validateLifecycleSections(
        markdown: MarkdownDocument,
        path: Path,
        state: LedgerState,
        expectedSections: List<String>,
        checklistSection: String?,
        diagnostics: MutableList<Diagnostic>,
    ) {
        validateSectionOrder(markdown, path, expectedSections, diagnostics)
        markdown.section("Description")?.let { description ->
            if (description.content.isBlank()) {
                diagnostics +=
                    Diagnostic(
                        "LEDGER_DESCRIPTION_EMPTY",
                        "Description must not be empty",
                        path,
                        description.headingLine,
                    )
            }
        }
        if (checklistSection != null) {
            val section = markdown.section(checklistSection)
            val checkbox = Regex("^\\s*- \\[( |x|X)]\\s+.+?\\s*$")
            val entries =
                section
                    ?.content
                    ?.lineSequence()
                    ?.filter { checkbox.matches(it) }
                    ?.toList()
                    .orEmpty()
            if (section != null && entries.isEmpty()) {
                diagnostics +=
                    Diagnostic(
                        "LEDGER_CHECKLIST_EMPTY",
                        "$checklistSection must contain checkboxes",
                        path,
                        section.headingLine,
                    )
            }
            if (state == LedgerState.CLOSED && entries.any { !it.contains(Regex("\\[[xX]]")) }) {
                diagnostics +=
                    Diagnostic("LEDGER_CLOSED_CHECKLIST", "closed record has unchecked $checklistSection", path)
            }
        }
        markdown.section("Closure")?.let { closure ->
            val receiptResult = ClosureReceiptParser.parse(path, closure.content)
            diagnostics += receiptResult.diagnostics
            receiptResult.receipt?.let { receipt ->
                diagnostics += ClosureReceiptParser.validate(path, state, receipt)
            }
        }
    }

    private fun validateSectionOrder(
        markdown: MarkdownDocument,
        path: Path,
        expected: List<String>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val actual = markdown.sections.keys.toList()
        if (actual != expected) {
            diagnostics +=
                Diagnostic(
                    "DOC_SECTION_ORDER",
                    "H2 sections must be exactly: ${expected.joinToString()}",
                    path,
                )
        }
    }

    private fun validateTaskIdentity(
        path: Path,
        ref: TaskRef,
        markdown: MarkdownDocument,
        diagnostics: MutableList<Diagnostic>,
    ) {
        if (TaskRecordFileName.parse(path.fileName.toString())?.ref != ref) {
            diagnostics +=
                Diagnostic(
                    "TASK_FILENAME_REF",
                    "filename must be the canonical file for ${ref.value}",
                    path,
                )
        }
        if (markdown.heading?.startsWith("${ref.value}: ") != true) {
            diagnostics += Diagnostic("TASK_HEADING_REF", "H1 must begin with ${ref.value}: ", path)
        }
    }

    private fun validateRoadmapIdentity(
        path: Path,
        ref: RoadmapRef,
        markdown: MarkdownDocument,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val match = ROADMAP_FILE.matchEntire(path.fileName.toString())
        if (match == null) {
            diagnostics +=
                Diagnostic("ROADMAP_FILENAME", "filename must match ROADMAP.<namespace>.<NNN>.<slug>.md", path)
        } else {
            val expected = "ROADMAP.${match.groupValues[1]}.${match.groupValues[2]}"
            if (expected !=
                ref.value
            ) {
                diagnostics += Diagnostic("ROADMAP_FILENAME_REF", "filename and ref disagree", path)
            }
        }
        if (markdown.heading?.startsWith(ref.value) != true) {
            diagnostics += Diagnostic("ROADMAP_HEADING_REF", "H1 must begin with ${ref.value}", path)
        }
    }

    private fun validateEpicIdentity(
        path: Path,
        ref: EpicRef,
        markdown: MarkdownDocument,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val match = EPIC_FILE.matchEntire(path.fileName.toString())
        if (match == null) {
            diagnostics += Diagnostic("EPIC_FILENAME", "filename must match EPIC.<namespace>.<NNN>.<slug>.md", path)
        } else {
            val expected = "EPIC.${match.groupValues[1]}.${match.groupValues[2]}"
            if (expected != ref.value) diagnostics += Diagnostic("EPIC_FILENAME_REF", "filename and ref disagree", path)
        }
        if (markdown.heading?.startsWith(ref.value) != true) {
            diagnostics += Diagnostic("EPIC_HEADING_REF", "H1 must begin with ${ref.value}", path)
        }
    }

    private fun validateTaskRelations(
        tasks: List<TaskDocument>,
        roadmaps: List<RoadmapDocument>,
        epics: List<EpicDocument>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        tasks.groupBy { it.ref }.filterValues { it.size > 1 }.toSortedMap().forEach { (ref, duplicates) ->
            diagnostics +=
                Diagnostic(
                    "TASK_REF_DUPLICATE",
                    "duplicate ref $ref in ${duplicates.map { layout.display(it.path) }.sorted().joinToString()}",
                )
        }
        tasks
            .groupBy {
                it.path.fileName
                    .toString()
                    .lowercase()
            }.filterValues { it.size > 1 }
            .forEach { (_, duplicates) ->
                diagnostics +=
                    Diagnostic(
                        "TASK_FILENAME_CASE_COLLISION",
                        "case-insensitive filename collision: ${duplicates.map {
                            layout.display(
                                it.path,
                            )
                        }.sorted().joinToString()}",
                    )
            }
        roadmaps.groupBy { it.ref }.filterValues { it.size > 1 }.toSortedMap().forEach { (ref, duplicates) ->
            diagnostics +=
                Diagnostic(
                    "ROADMAP_REF_DUPLICATE",
                    "duplicate ref $ref in ${duplicates.map { layout.display(it.path) }.sorted().joinToString()}",
                )
        }
        epics.groupBy { it.ref }.filterValues { it.size > 1 }.toSortedMap().forEach { (ref, duplicates) ->
            diagnostics +=
                Diagnostic(
                    "EPIC_REF_DUPLICATE",
                    "duplicate ref $ref in ${duplicates.map { layout.display(it.path) }.sorted().joinToString()}",
                )
        }
        val tasksByRef = tasks.groupBy { it.ref }.mapValues { (_, values) -> values.first() }
        val roadmapsByRef = roadmaps.groupBy { it.ref }.mapValues { (_, values) -> values.first() }
        val epicsByRef = epics.groupBy { it.ref }.mapValues { (_, values) -> values.first() }
        tasks.forEach { task ->
            if (task.roadmap !in roadmapsByRef) {
                diagnostics += Diagnostic("TASK_ROADMAP_MISSING", "unknown roadmap ${task.roadmap}", task.path)
            } else if (task.state == LedgerState.OPEN &&
                roadmapsByRef.getValue(task.roadmap).state == LedgerState.CLOSED
            ) {
                diagnostics +=
                    Diagnostic("TASK_ROADMAP_CLOSED", "open task belongs to closed roadmap ${task.roadmap}", task.path)
            }
            task.depends.forEach { dependency ->
                val dependencyTask = tasksByRef[dependency]
                when {
                    dependency == task.ref -> {
                        diagnostics +=
                            Diagnostic("TASK_DEPENDENCY_SELF", "task depends on itself", task.path)
                    }

                    dependencyTask == null -> {
                        diagnostics +=
                            Diagnostic("TASK_DEPENDENCY_MISSING", "unknown dependency $dependency", task.path)
                    }

                    task.state == LedgerState.CLOSED && dependencyTask.state == LedgerState.OPEN -> {
                        diagnostics +=
                            Diagnostic(
                                "TASK_CLOSED_DEPENDS_OPEN",
                                "closed task depends on open task $dependency",
                                task.path,
                            )
                    }
                }
            }
            validateRealization(task, tasks, tasksByRef, diagnostics)
        }
        roadmaps.filter { it.state == LedgerState.CLOSED }.forEach { roadmap ->
            val openMembers = tasks.filter { it.roadmap == roadmap.ref && it.state == LedgerState.OPEN }
            if (openMembers.isNotEmpty()) {
                diagnostics +=
                    Diagnostic(
                        "ROADMAP_CLOSED_WITH_OPEN_TASKS",
                        "closed roadmap has open tasks: ${openMembers.map { it.ref }.sorted().joinToString()}",
                        roadmap.path,
                    )
            }
        }
        roadmaps.forEach { roadmap ->
            val epic = roadmap.epic
            if (epic == null || epic !in epicsByRef) {
                diagnostics += Diagnostic("ROADMAP_EPIC_MISSING", "unknown epic ${epic ?: "<invalid>"}", roadmap.path)
            } else if (roadmap.state == LedgerState.OPEN && epicsByRef.getValue(epic).state == LedgerState.CLOSED) {
                diagnostics +=
                    Diagnostic("ROADMAP_EPIC_CLOSED", "open roadmap belongs to closed epic $epic", roadmap.path)
            }
        }
        roadmaps.groupBy { it.epic }.forEach { (epic, members) ->
            members.groupBy { it.ordinal }.filterValues { it.size > 1 }.toSortedMap().forEach { (ordinal, duplicates) ->
                diagnostics +=
                    Diagnostic(
                        "ROADMAP_ORDINAL_DUPLICATE",
                        "ordinal $ordinal is duplicated in ${epic ?: "<invalid>"}: ${duplicates.map {
                            it.ref
                        }.sorted().joinToString()}",
                    )
            }
        }
        epics.filter { it.state == LedgerState.CLOSED }.forEach { epic ->
            val openRoadmaps = roadmaps.filter { it.epic == epic.ref && it.state == LedgerState.OPEN }
            if (openRoadmaps.isNotEmpty()) {
                diagnostics +=
                    Diagnostic(
                        "EPIC_CLOSED_WITH_OPEN_ROADMAPS",
                        "closed epic has open roadmaps: ${openRoadmaps.map { it.ref }.sorted().joinToString()}",
                        epic.path,
                    )
            }
        }
    }

    private fun validateRealization(
        task: TaskDocument,
        tasks: List<TaskDocument>,
        tasksByRef: Map<TaskRef, TaskDocument>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val aggregateRef = task.realizes ?: return
        val aggregate = tasksByRef[aggregateRef]
        when {
            aggregateRef == task.ref -> {
                diagnostics += Diagnostic("TASK_REALIZATION_SELF", "task cannot realize itself", task.path)
            }

            aggregate == null -> {
                diagnostics +=
                    Diagnostic(
                        "TASK_REALIZATION_MISSING",
                        "unknown aggregate task $aggregateRef",
                        task.path,
                    )
            }

            aggregate.roadmap != task.roadmap -> {
                diagnostics +=
                    Diagnostic(
                        "TASK_REALIZATION_ROADMAP",
                        "realization and aggregate must belong to ${task.roadmap}",
                        task.path,
                    )
            }

            task.state == LedgerState.OPEN && aggregate.state == LedgerState.CLOSED -> {
                diagnostics +=
                    Diagnostic(
                        "TASK_REALIZATION_CLOSED_AGGREGATE",
                        "open task cannot realize closed aggregate $aggregateRef",
                        task.path,
                    )
            }
        }
        if (aggregate?.realizes != null || tasks.any { candidate -> candidate.realizes == task.ref }) {
            diagnostics +=
                Diagnostic(
                    "TASK_REALIZATION_NESTED",
                    "realization depth is limited to one level",
                    task.path,
                )
        }
        if (aggregate != null) {
            val dependencyClosure = transitiveDependencies(task, tasksByRef)
            val missingPrerequisites = aggregate.depends.filter { it !in dependencyClosure }
            if (missingPrerequisites.isNotEmpty()) {
                diagnostics +=
                    Diagnostic(
                        "TASK_REALIZATION_PREREQUISITE",
                        "realization dependency closure omits aggregate prerequisites: " +
                            missingPrerequisites.joinToString(),
                        task.path,
                    )
            }
        }
    }

    private fun transitiveDependencies(
        task: TaskDocument,
        tasksByRef: Map<TaskRef, TaskDocument>,
    ): Set<TaskRef> {
        val seen = mutableSetOf<TaskRef>()
        val pending = ArrayDeque(task.depends)
        while (pending.isNotEmpty()) {
            val dependency = pending.removeFirst()
            if (!seen.add(dependency)) continue
            tasksByRef[dependency]?.depends?.forEach(pending::addLast)
        }
        return seen
    }

    private fun markdownFiles(directory: Path): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
                .sorted(compareBy { it.fileName.toString() })
                .toList()
        }
    }

    private data class ParsedMarkdown(
        val frontMatter: FrontMatterDocument,
        val markdown: MarkdownDocument,
    )

    private companion object {
        const val AGENT_SCHEMA: String = "loom.agent/v1"
        val ROADMAP_FILE: Regex = Regex("ROADMAP\\.([a-z][a-z0-9-]*)\\.(\\d{3})\\.([a-z0-9]+(?:-[a-z0-9]+)*)\\.md")
        val EPIC_FILE: Regex = Regex("EPIC\\.([a-z][a-z0-9-]*)\\.(\\d{3})\\.([a-z0-9]+(?:-[a-z0-9]+)*)\\.md")
        val TASK_FIELD_ORDER: List<String> =
            listOf(
                "kind",
                "schema",
                "ref",
                "roadmap",
                "effort",
                "impact",
                "depends",
                "realizes",
            )
        val TASK_OPTIONAL_FIELDS: Set<String> = setOf("realizes")
        val ROADMAP_FIELD_ORDER: List<String> = listOf("kind", "schema", "ref", "epic", "ordinal")
        val EPIC_FIELD_ORDER: List<String> = listOf("kind", "schema", "ref")
        val TASK_FIELDS: Set<String> = TASK_FIELD_ORDER.toSet()
        val ROADMAP_FIELDS: Set<String> = ROADMAP_FIELD_ORDER.toSet()
        val EPIC_FIELDS: Set<String> = EPIC_FIELD_ORDER.toSet()
        val TASK_SECTION_ORDER: List<String> = listOf("Description", "Requirements", "Deliverables", "Closure")
        val ROADMAP_SECTION_ORDER: List<String> = listOf("Description", "Outcomes", "Exit Criteria", "Closure")
        val EPIC_SECTION_ORDER: List<String> = listOf("Description", "Outcomes", "Boundaries", "Closure")

        inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
            enumValues<T>().singleOrNull { it.name == value }
    }
}
