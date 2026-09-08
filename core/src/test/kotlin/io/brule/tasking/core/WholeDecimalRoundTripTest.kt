package io.brule.tasking.core

import java.math.BigDecimal
import kotlin.test.*

class WholeDecimalRoundTripTest {
    @Test fun `whole decimal extension values retain their numeric type and exact value`() {
        for (source in listOf("1E+3", "1000", "0E+3", "-2E+30", "1.00E+5", "1.2300", "1E-20")) {
            val value = obj("number" to DecimalValue(BigDecimal(source)))
            val encoded = Json.encode(value)
            assertEquals(value, YamlValues.parse(encoded).value, "$source encoded as $encoded")
        }
    }

    @Test fun `explicit scientific numeric tokens cross the composed decoder as decimals`() {
        for (source in listOf("1E+3", "1000E+0", "0E+3", "-2E+30")) {
            val actual = YamlValues.parse("{\"number\":$source}").value as ObjectValue
            assertEquals(DecimalValue(BigDecimal(source)), actual.fields["number"])
        }
    }
}
