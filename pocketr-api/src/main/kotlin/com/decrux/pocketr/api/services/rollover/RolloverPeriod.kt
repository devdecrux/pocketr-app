package com.decrux.pocketr.api.services.rollover

import java.time.LocalDate
import java.time.YearMonth

data class RolloverPeriod(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
) {
    companion object {
        fun containing(
            anchorDate: LocalDate,
            rolloverDay: Int,
        ): RolloverPeriod {
            validateRolloverDay(rolloverDay)

            val anchorMonth = YearMonth.from(anchorDate)
            val currentStart = anchorMonth.atClampedDay(rolloverDay)
            val startMonth = if (anchorDate.isBefore(currentStart)) anchorMonth.minusMonths(1) else anchorMonth
            return startingIn(startMonth, rolloverDay)
        }

        fun startingIn(
            period: YearMonth,
            rolloverDay: Int,
        ): RolloverPeriod {
            validateRolloverDay(rolloverDay)

            return RolloverPeriod(
                startInclusive = period.atClampedDay(rolloverDay),
                endExclusive = period.plusMonths(1).atClampedDay(rolloverDay),
            )
        }

        fun validateRolloverDay(rolloverDay: Int) {
            require(rolloverDay in 1..31) { "rolloverDay must be between 1 and 31" }
        }

        private fun YearMonth.atClampedDay(day: Int): LocalDate = atDay(day.coerceAtMost(lengthOfMonth()))
    }
}
