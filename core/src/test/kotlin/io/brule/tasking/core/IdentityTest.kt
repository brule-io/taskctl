package io.brule.tasking.core

import kotlin.test.*

class IdentityTest {
    @Test fun `native identities validate once retain complete suffixes and cannot cross nominal kinds`() {
        val full = "TASK.process.006.workflow-canon-backport"
        assertEquals(full, TaskId.parse(full)?.value)
        assertEquals("TASK.M2.nginx", TaskId.parse("TASK.M2.nginx")?.value)
        assertEquals("ROADMAP.delivery", RoadmapId.parse("ROADMAP.delivery")?.value)
        assertEquals("EPIC.delivery", EpicId.parse("EPIC.delivery")?.value)
        for (raw in listOf("banana", "🦍", "", "TASK.", "TASK../escape", "TASK.a b", "TASK.a\n")) {
            assertNull(TaskId.parse(raw)); assertNull(RoadmapId.parse(raw)); assertNull(EpicId.parse(raw))
        }
        assertNull(TaskId.parse("EPIC.delivery"))
        assertNull(RoadmapId.parse("TASK.delivery"))
        assertNull(EpicId.parse("ROADMAP.delivery"))
        assertNull(ContractDigest.parse("pretend-hash"))
        assertNull(Revision.parse("banana"))
        assertEquals(Revision.initial(), Revision.parse(Revision.initial().value))
    }
}
