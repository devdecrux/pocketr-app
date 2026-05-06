package com.decrux.pocketr.api.services.user_preferences

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.dtos.UpdateRolloverDayDto
import com.decrux.pocketr.api.entities.dtos.UpdateUserLanguageDto
import com.decrux.pocketr.api.entities.dtos.UserDto

interface ManageUserPreferences {
    fun updateLanguage(
        user: User,
        dto: UpdateUserLanguageDto,
    ): UserDto

    fun updateRolloverDay(
        user: User,
        dto: UpdateRolloverDayDto,
    ): UserDto
}
