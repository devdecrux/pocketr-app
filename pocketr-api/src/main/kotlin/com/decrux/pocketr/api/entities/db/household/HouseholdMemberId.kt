package com.decrux.pocketr.api.entities.db.household

import java.io.Serializable
import java.util.UUID

data class HouseholdMemberId(
    var household: UUID? = null,
    var user: Long? = null,
) : Serializable
