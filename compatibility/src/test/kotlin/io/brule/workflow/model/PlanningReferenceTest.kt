package io.brule.workflow.model

import kotlin.test.*

class PlanningReferenceTest {
    @Test fun `every nominal legacy reference enters through its own validated grammar`() {
        assertEquals("ROADMAP.release.001", RoadmapRef.parse("ROADMAP.release.001")?.value)
        assertEquals("EPIC.release.001", EpicRef.parse("EPIC.release.001")?.value)
        assertEquals("ADR.0042", AdrRef.parse("ADR.0042")?.value)
        for (value in listOf("banana", "absolutely-not-an-epic", "🦍", "", " TASK.release.001", "TASK.release.01")) {
            assertNull(TaskRef.parse(value)); assertNull(RoadmapRef.parse(value))
            assertNull(EpicRef.parse(value)); assertNull(AdrRef.parse(value))
        }
        assertNull(RoadmapRef.parse("EPIC.release.001"))
        assertNull(EpicRef.parse("ROADMAP.release.001"))
        assertNull(AdrRef.parse("TASK.release.001"))
        assertNull(RoadmapRef.parse("ROADMAP.release.0001"))
        assertNull(EpicRef.parse("EPIC.Release.001"))
        assertNull(AdrRef.parse("ADR.042"))
    }
}
