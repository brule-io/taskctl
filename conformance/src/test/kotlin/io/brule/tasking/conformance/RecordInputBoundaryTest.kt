package io.brule.tasking.conformance

import io.brule.tasking.core.*
import io.brule.tasking.repository.Bootstrap
import io.brule.tasking.repository.FileTaskLedger
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class RecordInputBoundaryTest {
    @TempDir lateinit var directory: Path
    private fun record(requirements: List<String> = listOf("Bounded result"), acceptance: List<String> = listOf("Observed"), required: List<String> = emptyList()) =
        DraftRecord(TaskId.parseOrThrow("TASK.input.boundary"), "Boundary", "open", "Retain constructor validity", emptyList(), requirements, acceptance, required, obj(), "tasking/core-draft-2", listOf("test"))
    private fun plan(seed: Transition.AddRecords): io.brule.tasking.repository.InitializationPlan {
        val distribution = directory.resolve("distribution/bootstrap"); Files.createDirectories(distribution)
        listOf("taskctl", "taskctl.ps1", "taskctl.bat").forEach { if (!Files.exists(distribution.resolve(it))) Files.writeString(distribution.resolve(it), "fixture\n") }
        val lock = "lockFormat=2\nwrapperVersion=3\ntoolVersion=test\nwindows-x86_64.url=file:///fixture.zip\nwindows-x86_64.sha256=${"0".repeat(64)}\n"
        return Bootstrap.plan(directory.resolve("target"), "input.boundary", "test", distribution.parent, lock, seed)
    }
    private fun image(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString() to Pair(Files.readString(it), Files.getLastModifiedTime(it))
    } }
    @Test fun `post construction invalid record lists cannot become initialization plans`() {
        val requirements = mutableListOf("Valid"); val first = record(requirements = requirements); requirements.clear()
        val acceptance = mutableListOf("Valid"); val second = record(acceptance = acceptance); acceptance[0] = " "
        val required = mutableListOf<String>(); val third = record(required = required); required += "banana"
        for (record in listOf(first, second, third)) {
            assertFails { plan(Transition.AddRecords(listOf(record))) }
            assertFalse(Files.exists(directory.resolve("target")))
        }
    }
    @Test fun `file ledger refuses mutated task and planning inputs without writing`() {
        Bootstrap.apply(plan(Transition.AddRecords(listOf(record()))))
        val root = directory.resolve("target"); Files.writeString(root.resolve("product.txt"), "Untouched\n")
        val ledger = FileTaskLedger(root); val snapshot = ledger.snapshot(); val before = image(root)
        val requirements = mutableListOf("Valid"); val task = record(requirements = requirements); requirements.clear()
        assertFails { ledger.plan(snapshot.revision, Transition.ReviseTask(task)) }
        assertFails { ledger.apply(snapshot.revision, Transition.ReviseTask(task)) }
        val required = mutableListOf<String>()
        val roadmap = DraftRoadmap(RoadmapId.parseOrThrow("ROADMAP.input"), "Input", "Bounded", listOf(task.id), requiredExtensions = required)
        required += "banana"
        assertFails { ledger.plan(snapshot.revision, Transition.AddRecords(roadmaps = listOf(roadmap))) }
        assertFails { ledger.apply(snapshot.revision, Transition.AddRecords(roadmaps = listOf(roadmap))) }
        assertEquals(before, image(root)); assertEquals(snapshot, FileTaskLedger(root).snapshot())
    }
}
