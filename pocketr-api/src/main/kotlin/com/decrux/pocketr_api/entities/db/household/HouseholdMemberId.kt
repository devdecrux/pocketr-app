package com.decrux.pocketr_api.entities.db.household

import java.io.Serializable
import java.util.UUID

data class HouseholdMemberId(
    var household: UUID? = null,
    var user: Long? = null,
) : Serializable
