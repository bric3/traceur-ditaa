// SPDX-License-Identifier: MPL-2.0
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.tabletest.junit.TableTest

class TestSlotTest {
    @TableTest(
        "invalid",
        "'0'",
        "'0/0'",
        "'-1/4'",
        "'4/4'",
        "'1/-2'",
        "'a/4'",
        "'0/4/1'",
        "'2147483648/4'"
    )
    fun `rejects invalid slots`(invalid: String) {
        assertThrows(IllegalArgumentException::class.java) { TestSlot.parse(invalid) }
    }

    @TableTest("count", "1", "2", "3", "4", "7", "31")
    fun `every hash belongs to exactly one slot`(count: Int) {
        for (prefix in listOf(0L, 1L, 2L, 3L, 4L, 2147483647L, 2147483648L, 4294967295L)) {
            val id = prefix.toString(16).padStart(8, '0') + "0".repeat(56)
            val matching = (0 until count).filter { TestSlot.parse("$it/$count").includes(id) }
            assertEquals(listOf((prefix % count).toInt()), matching)
        }
    }
}
