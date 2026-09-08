package io.brule.tasking.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class UnicodeValueTest {
    @Test fun `ill formed UTF16 cannot enter string values or object keys`() {
        for (invalid in listOf("\uD800", "\uDC00", "prefix\uD800suffix", "\uDC00\uD800", "\uD800\uD800")) {
            assertFails { StringValue(invalid) }
            assertFails { ObjectValue(mapOf(invalid to NullValue)) }
        }
    }

    @Test fun `escaped unpaired surrogates fail at the decoding boundary`() {
        for (escaped in listOf("\\uD800", "\\uDC00", "\\uD800x", "\\uDC00\\uD800")) {
            assertFails { YamlValues.parse("\"$escaped\"") }
            assertFails { YamlValues.parse("{\"$escaped\":null}") }
        }
    }

    @Test fun `valid scalar controls and supplementary text survive JSON and YAML boundaries`() {
        val points = listOf(0, 1, 31, 32, 126, 127, 128, 133, 159, 160, 0xd7ff, 0xe000,
            0x2028, 0x2029, 0xfffd, 0xfffe, 0xffff, 0x10000, 0x1f680, 0x10ffff)
        for (point in points) {
            val text = String(Character.toChars(point))
            val value = obj(text to StringValue("before${text}after"))
            assertEquals(value, YamlValues.parse(Json.encode(value)).value, "Unicode scalar U+${point.toString(16)}")
        }
        val composed = StringValue("é")
        val decomposed = StringValue("e\u0301")
        assertNotEquals(Canonical.digest("unicode", composed), Canonical.digest("unicode", decomposed))
    }

    @Test fun `well formed Unicode retains its prior canonical digest`() {
        val value = StringValue("A \uD83D\uDE80 \u0000 \uD7FF \uE000 e\u0301 é \u2028 \u2029")
        assertEquals("sha256:a7627308d7d5ec5564790f72d81570e32216cad513b29d7d40489f6ba8b4e857",
            Canonical.digest("taskctl.unicode-conformance/1", value))
        assertEquals(value, YamlValues.parse(Json.encode(value)).value)
    }
}
