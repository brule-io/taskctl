package io.brule.workflow.io

import io.brule.workflow.diagnostics.WorkflowOperationException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute

public data class RepositoryLayout(
    public val root: Path,
) {
    public val agentsDirectory: Path = root.resolve(".agents")
    public val openTasksDirectory: Path = agentsDirectory.resolve("tasks/open")
    public val closedTasksDirectory: Path = agentsDirectory.resolve("tasks/closed")
    public val openRoadmapsDirectory: Path = agentsDirectory.resolve("roadmaps/open")
    public val closedRoadmapsDirectory: Path = agentsDirectory.resolve("roadmaps/closed")
    public val openEpicsDirectory: Path = agentsDirectory.resolve("epics/open")
    public val closedEpicsDirectory: Path = agentsDirectory.resolve("epics/closed")
    public val workflowLock: Path = agentsDirectory.resolve(".lock")

    public val adrDirectory: Path = root.resolve("docs/arch/adr")
    public val proposedAdrsDirectory: Path = adrDirectory.resolve("proposed")
    public val acceptedAdrsDirectory: Path = adrDirectory.resolve("accepted")
    public val rejectedAdrsDirectory: Path = adrDirectory.resolve("rejected")
    public val supersededAdrsDirectory: Path = adrDirectory.resolve("superseded")

    public fun display(path: Path): String {
        val normalized = path.toAbsolutePath().normalize()
        val relative = if (normalized.startsWith(root)) root.relativize(normalized) else normalized
        return relative.toString().replace('\\', '/')
    }

    public companion object {
        public fun at(root: Path): RepositoryLayout = RepositoryLayout(root.absolute().normalize())

        public fun discover(start: Path = Path.of("")): RepositoryLayout {
            var candidate: Path? = start.absolute().normalize()
            while (candidate != null) {
                if (Files.exists(candidate.resolve(".git"))) return at(candidate)
                candidate = candidate.parent
            }
            throw WorkflowOperationException("unable to locate repository root from ${start.absolute().normalize()}")
        }
    }
}
