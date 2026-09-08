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
}
