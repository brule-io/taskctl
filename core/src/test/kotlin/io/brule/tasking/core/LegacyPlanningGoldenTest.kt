package io.brule.tasking.core

import kotlin.test.*

class LegacyPlanningGoldenTest {
    private fun source(name: String) = requireNotNull(javaClass.getResourceAsStream("/planning-native/$name.json"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    @Test fun `native legacy planning values retain their exact pre-amendment encoding`() {
        val provenance = YamlValues.parse(source("sources")).value as ObjectValue
        assertEquals("3e504e61aaf040de3aa69f531f47785657ff40a3", provenance.requiredString("producer_source"))
        val hashes = provenance.fields.getValue("files") as ObjectValue
        for (kind in listOf("roadmaps", "epics")) {
            val original = source(kind)
            val value = PlanningRecordCodec.decode(YamlValues.parse(original).value as ObjectValue)
            assertEquals(PlanningRecordCodec.PROTOCOL, value.protocol)
            assertTrue(value.acceptance.isEmpty())
            assertEquals(original, Json.encode(PlanningRecordCodec.encode(value)) + "\n")
            assertEquals(hashes.requiredString(kind), Canonical.sha256(original.toByteArray(Charsets.UTF_8)))
            val encoded = PlanningRecordCodec.encode(value)
            assertFalse("disposition" in encoded.fields || "acceptance" in encoded.fields)
            assertFails { PlanningRecordCodec.decode(ObjectValue(encoded.fields + ("acceptance" to strings(listOf("Cannot retrofit a prior protocol"))))) }
            assertFails { PlanningRecordCodec.decode(ObjectValue(encoded.fields + ("disposition" to StringValue("archived")))) }
        }
    }
}
