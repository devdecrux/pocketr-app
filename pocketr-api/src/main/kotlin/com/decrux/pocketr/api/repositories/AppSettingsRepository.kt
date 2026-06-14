package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.settings.AppSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppSettingsRepository : JpaRepository<AppSettings, Short>
