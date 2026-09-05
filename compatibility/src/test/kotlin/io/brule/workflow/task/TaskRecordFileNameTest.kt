package io.brule.workflow.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TaskRecordFileNameTest {
    @Test
    fun `numbered task filename preserves ref and prose slug`() {
        val parsed = TaskRecordFileName.parse("TASK.governance.001.admit-governance-baseline.md")

        assertEquals("TASK.governance.001", parsed?.ref?.value)
        assertEquals("admit-governance-baseline", parsed?.slug)
    }

    @Test
    fun `task filenames reject missing and noncanonical slugs`() {
        listOf(
            "TASK.governance.001.md",
            "TASK.governance.001.Mixed.md",
            "TASK.governance.001.repeated--separator.md",
            "TASK.governance.001.admit.txt",
        ).forEach { fileName -> assertNull(TaskRecordFileName.parse(fileName), fileName) }
    }
}
