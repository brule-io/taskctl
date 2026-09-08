package io.brule.tasking.conformance

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ImportBorrowingTest {
    @TempDir lateinit var directory: Path

    @Test fun `mutating borrowed manifest bytes after review cannot produce an import plan`() {
        val original = ComplexPlanningFixture.inspect()
        val borrowed = original.files.toMutableMap()
        val manifest = original.copy(files = borrowed)
        val admission = ComplexPlanningFixture.admission(manifest)
        val reviewedId = manifest.id
        borrowed["README.md"] = "Changed after the review and cached manifest identity.\n"
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { Files.writeString(distribution.resolve(it), "fixture\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        assertEquals(reviewedId, admission.review.manifest)
        assertFails { Bootstrap.plan(directory.resolve("target"), "conformance.borrowing", "test", distribution.parent, lock, imported = admission) }
        assertFalse(Files.exists(directory.resolve("target")))
    }

    @Test fun `mutating projected records or provenance after review is rejected by the reducer`() {
        val original = ComplexPlanningFixture.inspect()
        val tasks = original.universe.tasks.toMutableList()
        val manifest = original.copy(universe = original.universe.copy(tasks = tasks))
        val admission = ComplexPlanningFixture.admission(manifest)
        val empty = LedgerSnapshot("conformance.borrowing", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
        tasks[0] = tasks[0].copy(requirements = listOf("New unreviewed semantic requirement"))
        assertFails { LedgerTransitions.evolve(empty, Transition.ImportRecords(admission)) }
        val sources = original.sources.toMutableList()
        val sourceAdmission = ComplexPlanningFixture.admission(original.copy(sources = sources))
        sources[0] = sources[0].copy(sha256 = "0".repeat(64))
        assertFails { LedgerTransitions.evolve(empty, Transition.ImportRecords(sourceAdmission)) }
        assertTrue(empty.history!!.heads.isEmpty())
    }

    @Test fun `a newly constructed exact manifest and explicit new review remains admissible`() {
        val original = ComplexPlanningFixture.inspect()
        val changed = original.copy(files = original.files + ("README.md" to "Reviewed additional witness text.\n"))
        assertNotEquals(original.id, changed.id)
        assertFails { ImportAdmission(changed, ComplexPlanningFixture.admission(original).review) }
        val admission = ComplexPlanningFixture.admission(changed)
        admission.validate()
        val empty = LedgerSnapshot("conformance.borrowing", Revision.initial(), DraftUniverse(emptyList()), history = TaskHistory(Revision.initial()))
        val imported = LedgerTransitions.evolve(empty, Transition.ImportRecords(admission))
        assertEquals(changed, imported.imports.single().manifest)
    }
}
