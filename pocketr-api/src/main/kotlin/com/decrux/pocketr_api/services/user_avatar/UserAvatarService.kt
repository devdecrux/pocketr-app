package com.decrux.pocketr_api.services.user_avatar

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.services.storage.StorageInitializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Locale
import java.util.UUID

@Service
class UserAvatarService(
    @Value("\${pocketr.avatar.storage-dir:storage/avatars}")
    avatarStorageDir: String,
    storageInitializer: StorageInitializer,
) {

    private val avatarStoragePath: Path = storageInitializer.ensureWritableDirectory(
        Paths.get(avatarStorageDir),
    )

    fun storeAvatar(user: User, avatar: MultipartFile): String {
        if (avatar.isEmpty) {
            throw IllegalArgumentException("Avatar file is required")
        }
        if (avatar.size > MAX_AVATAR_SIZE_BYTES) {
            throw IllegalArgumentException("Avatar file is too large. Maximum size is 5MB")
        }

        val contentType = validateAvatarType(avatar)
        val filename = buildFilename(
            userId = requireNotNull(user.userId) { "Authenticated user id is required" },
            contentType = contentType,
        )
        val targetPath = avatarStoragePath.resolve(filename).normalize()

        if (!targetPath.startsWith(avatarStoragePath)) {
            throw IllegalArgumentException("Invalid avatar path")
        }

        try {
            avatar.inputStream.use { inputStream ->
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to store avatar", e)
        }

        deleteAvatarIfExists(user.avatarPath)
        return filename
    }

    fun resolveAvatarDataUrl(storedPath: String?): String? {
        if (storedPath.isNullOrBlank()) {
            return null
        }

        val resolvedPath = avatarStoragePath.resolve(storedPath).normalize()
        if (
            !resolvedPath.startsWith(avatarStoragePath) ||
            !Files.exists(resolvedPath) ||
            !Files.isRegularFile(resolvedPath)
        ) {
            return null
        }

        return try {
            val bytes = Files.readAllBytes(resolvedPath)
            val contentType = resolveContentType(resolvedPath)

            "data:$contentType;base64,${Base64.getEncoder().encodeToString(bytes)}"
        } catch (_: IOException) {
            null
        }
    }

    private fun validateAvatarType(avatar: MultipartFile): String {
        val contentType = avatar.contentType?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("Avatar content type is required")

        if (contentType !in CONTENT_TYPE_TO_EXTENSION) {
            throw IllegalArgumentException(
                "Unsupported avatar format. Allowed formats: JPEG, PNG, GIF, WEBP",
            )
        }

        return contentType
    }

    private fun deleteAvatarIfExists(storedPath: String?) {
        if (storedPath.isNullOrBlank()) {
            return
        }

        val resolvedPath = avatarStoragePath.resolve(storedPath).normalize()
        if (!resolvedPath.startsWith(avatarStoragePath)) {
            return
        }

        try {
            Files.deleteIfExists(resolvedPath)
        } catch (_: IOException) {
            // Ignore delete errors to avoid failing a successful upload.
        }
    }

    private fun buildFilename(userId: Long, contentType: String): String {
        val extension = CONTENT_TYPE_TO_EXTENSION[contentType]
            ?: throw IllegalArgumentException("Unsupported avatar format")

        return "user-$userId-${UUID.randomUUID()}$extension"
    }

    private fun resolveContentType(path: Path): String {
        val detectedType = Files.probeContentType(path)
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        if (detectedType != null) {
            return detectedType
        }

        val fileName = path.fileName.toString().lowercase(Locale.ROOT)
        return CONTENT_TYPE_TO_EXTENSION
            .entries
            .firstOrNull { (_, extension) -> fileName.endsWith(extension) }
            ?.key
            ?: "application/octet-stream"
    }

    private companion object {
        const val MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024

        val CONTENT_TYPE_TO_EXTENSION: Map<String, String> = mapOf(
            "image/jpeg" to ".jpg",
            "image/png" to ".png",
            "image/gif" to ".gif",
            "image/webp" to ".webp",
        )
    }
}
