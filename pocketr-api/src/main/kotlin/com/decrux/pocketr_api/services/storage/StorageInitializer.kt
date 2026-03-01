package com.decrux.pocketr_api.services.storage

import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@Component
class StorageInitializer {
    fun ensureWritableDirectory(storagePath: Path): Path {
        val normalizedPath = storagePath.toAbsolutePath().normalize()
        return try {
            normalizedPath.parent?.let { Files.createDirectories(it) }
            Files.createDirectories(normalizedPath)
            if (!Files.isDirectory(normalizedPath) || !Files.isWritable(normalizedPath)) {
                throw IllegalStateException("Storage directory is not writable: $normalizedPath")
            }
            normalizedPath
        } catch (e: IOException) {
            throw IllegalStateException("Unable to create storage directory: $normalizedPath", e)
        }
    }
}
