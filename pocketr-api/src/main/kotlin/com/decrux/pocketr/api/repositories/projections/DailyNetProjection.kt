package com.decrux.pocketr.api.repositories.projections

import java.time.LocalDate

data class DailyNetProjection(
    val txnDate: LocalDate,
    val netMinor: Long,
)
