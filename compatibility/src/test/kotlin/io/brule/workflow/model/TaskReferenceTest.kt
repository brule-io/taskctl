package io.brule.workflow.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TaskReferenceTest {
    @Test
    fun `ordinary numbered task reference is admitted exactly`() {
        assertEquals("TASK.governance.001", TaskRef.parse("TASK.governance.001")?.value)
    }

    @Test
    fun `noncanonical and qualified task references are rejected`() {
        listOf(
            "TASK.Governance.001",
            "TASK.governance.01",
            "TASK.governance.0001",
            "TASK.governance.001.extra",
            "TASK.arbitrary.dotted.reference",
            "TASK.governance.001/path",
        ).forEach { value -> assertNull(TaskRef.parse(value), value) }
    }
}
