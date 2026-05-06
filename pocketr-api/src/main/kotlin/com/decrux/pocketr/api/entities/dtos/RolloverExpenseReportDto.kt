package com.decrux.pocketr.api.entities.dtos

import java.time.LocalDate

data class RolloverExpenseReportDto(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val entries: List<MonthlyExpenseDto>,
)
