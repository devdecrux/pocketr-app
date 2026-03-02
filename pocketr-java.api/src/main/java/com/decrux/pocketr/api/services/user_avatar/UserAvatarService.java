package com.decrux.pocketr.api.services.user_avatar;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.UserDto;
import com.decrux.pocketr.api.repositories.UserRepository;
import com.decrux.pocketr.api.services.storage.StorageInitializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAvatarService {

    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("image/jpeg", ".jpg");
        map.put("image/png", ".png");
        map.put("image/gif", ".gif");
        map.put("image/webp", ".webp");
        CONTENT_TYPE_TO_EXTENSION = Map.copyOf(map);
    }

    private final Path avatarStoragePath;
    private final UserRepository userRepository;

    public UserAvatarService(
        @Value("${pocketr.avatar.storage-dir:storage/avatars}") String avatarStorageDir,
        StorageInitializer storageInitializer,
        UserRepository userRepository
    ) {
        this.avatarStoragePath = storageInitializer.ensureWritableDirectory(Paths.get(avatarStorageDir));
        this.userRepository = userRepository;
    }

    @Transactional
    public UserDto uploadAvatar(User authenticatedUser, MultipartFile avatar) {
        Long userId = authenticatedUser.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user id is missing");
        }

        User persistedUser = userRepository
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String storedPath;
        try {
            storedPath = storeAvatar(persistedUser, avatar);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                e.getMessage() != null ? e.getMessage() : "Invalid avatar upload request",
                e
            );
        }

        persistedUser.setAvatarPath(storedPath);
        User savedUser = userRepository.save(persistedUser);
        return toUserDto(savedUser);
    }

    public String storeAvatar(User user, MultipartFile avatar) {
        if (avatar.isEmpty()) {
            throw new IllegalArgumentException("Avatar file is required");
        }
        if (avatar.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new IllegalArgumentException("Avatar file is too large. Maximum size is 5MB");
        }

        String contentType = validateAvatarType(avatar);
        String filename = buildFilename(
            requireNotNull(user.getUserId(), "Authenticated user id is required"),
            contentType
        );
        Path targetPath = avatarStoragePath.resolve(filename).normalize();

        if (!targetPath.startsWith(avatarStoragePath)) {
            throw new IllegalArgumentException("Invalid avatar path");
        }

        try {
            try (var inputStream = avatar.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store avatar", e);
        }

        deleteAvatarIfExists(user.getAvatarPath());
        return filename;
    }

    private String validateAvatarType(MultipartFile avatar) {
        String contentType = avatar.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("Avatar content type is required");
        }
        String lowered = contentType.toLowerCase(Locale.ROOT);

        if (!CONTENT_TYPE_TO_EXTENSION.containsKey(lowered)) {
            throw new IllegalArgumentException("Unsupported avatar format. Allowed formats: JPEG, PNG, GIF, WEBP");
        }

        return lowered;
    }

    private String buildFilename(long userId, String contentType) {
        String extension = CONTENT_TYPE_TO_EXTENSION.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("Unsupported avatar format");
        }

        return "user-" + userId + "-" + UUID.randomUUID() + extension;
    }

    private void deleteAvatarIfExists(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }

        Path resolvedPath = avatarStoragePath.resolve(storedPath).normalize();
        if (!resolvedPath.startsWith(avatarStoragePath)) {
            return;
        }

        try {
            Files.deleteIfExists(resolvedPath);
        } catch (IOException ignored) {
            // Ignore delete errors to avoid failing a successful upload.
        }
    }

    public UserDto toUserDto(User user) {
        return new UserDto(
            user.getUserId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            resolveAvatarDataUrl(user.getAvatarPath())
        );
    }

    public String resolveAvatarDataUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }

        Path resolvedPath = avatarStoragePath.resolve(storedPath).normalize();
        if (
            !resolvedPath.startsWith(avatarStoragePath)
                || !Files.exists(resolvedPath)
                || !Files.isRegularFile(resolvedPath)
        ) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(resolvedPath);
            String contentType = resolveContentType(resolvedPath);
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String resolveContentType(Path path) {
        try {
            String detectedType = Files.probeContentType(path);
            if (detectedType != null) {
                String lowered = detectedType.toLowerCase(Locale.ROOT);
                if (!lowered.isBlank()) {
                    return lowered;
                }
            }
        } catch (IOException ignored) {
            // Fall through to extension-based resolution.
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : CONTENT_TYPE_TO_EXTENSION.entrySet()) {
            if (fileName.endsWith(entry.getValue())) {
                return entry.getKey();
            }
        }
        return "application/octet-stream";
    }

    private static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
