package com.decrux.pocketr.api.services.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class StorageInitializer {

    public Path ensureWritableDirectory(Path storagePath) {
        Path normalizedPath = storagePath.toAbsolutePath().normalize();
        try {
            Path parent = normalizedPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.createDirectories(normalizedPath);
            if (!Files.isDirectory(normalizedPath) || !Files.isWritable(normalizedPath)) {
                throw new IllegalStateException("Storage directory is not writable: " + normalizedPath);
            }
            return normalizedPath;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create storage directory: " + normalizedPath, e);
        }
    }
}
