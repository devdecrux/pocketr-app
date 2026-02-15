package com.decrux.pocketr_api.entities.db.household

import java.io.Serializable
import java.util.UUID

data class HouseholdAccountShareId(
    var household: UUID? = null,
    var account: UUID? = null,
) : Serializable
