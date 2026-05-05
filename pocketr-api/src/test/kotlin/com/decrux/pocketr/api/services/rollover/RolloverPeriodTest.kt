package com.decrux.pocketr.api.services.rollover

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class RolloverPeriodTest {
    @Test
    fun calendarMonthWhenRolloverDayIsOne() {
        val period = RolloverPeriod.containing(LocalDate.of(2026, 5, 15), 1)

        assertEquals(LocalDate.of(2026, 5, 1), period.startInclusive)
        assertEquals(LocalDate.of(2026, 6, 1), period.endExclusive)
    }

    @Test
    fun dateOnRolloverDayStartsNewPeriod() {
        val period = RolloverPeriod.containing(LocalDate.of(2026, 6, 25), 25)

        assertEquals(LocalDate.of(2026, 6, 25), period.startInclusive)
        assertEquals(LocalDate.of(2026, 7, 25), period.endExclusive)
    }

    @Test
    fun dateBeforeRolloverDayUsesPreviousPeriod() {
        val period = RolloverPeriod.containing(LocalDate.of(2026, 6, 24), 25)

        assertEquals(LocalDate.of(2026, 5, 25), period.startInclusive)
        assertEquals(LocalDate.of(2026, 6, 25), period.endExclusive)
    }

    @Test
    fun clampsRolloverDayThirtyToFebruaryEnd() {
        val period = RolloverPeriod.startingIn(YearMonth.of(2026, 2), 30)

        assertEquals(LocalDate.of(2026, 2, 28), period.startInclusive)
        assertEquals(LocalDate.of(2026, 3, 30), period.endExclusive)
    }

    @Test
    fun clampsRolloverDayThirtyOneToLeapFebruaryEnd() {
        val period = RolloverPeriod.startingIn(YearMonth.of(2028, 2), 31)

        assertEquals(LocalDate.of(2028, 2, 29), period.startInclusive)
        assertEquals(LocalDate.of(2028, 3, 31), period.endExclusive)
    }

    @Test
    fun rejectsInvalidRolloverDay() {
        assertThrows(IllegalArgumentException::class.java) {
            RolloverPeriod.containing(LocalDate.of(2026, 1, 1), 32)
        }
    }
}
