package io.brule.workflow.task

import io.brule.workflow.document.FrontMatterDocument
import io.brule.workflow.document.MarkdownDocument
import io.brule.workflow.model.EpicRef
import io.brule.workflow.model.RoadmapRef
import io.brule.workflow.model.TaskRef
import java.nio.file.Path

public enum class LedgerState {
    OPEN,
    CLOSED,
}

public enum class Effort(
    public val rank: Int,
) {
    LOW(0),
    MEDIUM(1),
    HIGH(2),
}

public enum class Impact(
    public val rank: Int,
) {
    HIGH(0),
    MEDIUM(1),
    LOW(2),
}

public data class TaskDocument(
    public val path: Path,
    public val state: LedgerState,
    public val ref: TaskRef,
    public val roadmap: RoadmapRef,
    public val effort: Effort,
    public val impact: Impact,
    public val depends: List<TaskRef>,
    public val realizes: TaskRef? = null,
    public val title: String,
    public val frontMatter: FrontMatterDocument,
    public val markdown: MarkdownDocument,
) {
    public companion object {
        public val ORDER: Comparator<TaskDocument> = compareBy { it.ref.value }
    }
}

public data class RoadmapDocument(
    public val path: Path,
    public val state: LedgerState,
    public val ref: RoadmapRef,
    public val epic: EpicRef?,
    public val ordinal: Int,
    public val title: String,
    public val frontMatter: FrontMatterDocument,
    public val markdown: MarkdownDocument,
)

public data class EpicDocument(
    public val path: Path,
    public val state: LedgerState,
    public val ref: EpicRef,
    public val title: String,
    public val frontMatter: FrontMatterDocument,
    public val markdown: MarkdownDocument,
)
