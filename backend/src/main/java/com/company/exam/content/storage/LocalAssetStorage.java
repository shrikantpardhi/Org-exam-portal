package com.company.exam.content.storage;

import com.company.exam.common.exception.SystemException;
import com.company.exam.content.config.ContentProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Local-filesystem {@link AssetStorage} ({@code specs/12-P3-Content-V1.5.md §100}).
 *
 * <p>Files are stored under the configured root. Path traversal is prevented: the resolved target
 * must stay inside the root.
 */
@Component
public class LocalAssetStorage implements AssetStorage {

    private final Path root;

    public LocalAssetStorage(ContentProperties properties) {
        this.root = Paths.get(properties.getAssetStorageRoot()).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID assetId, String fileName, String contentType, byte[] content) {
        String safeName = sanitize(fileName);
        String storedName = assetId + "-" + safeName;
        try {
            Files.createDirectories(root);
            Path target = root.resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                throw new SystemException("Refusing to store an asset outside the storage root",
                        new IllegalStateException("resolved path escapes the storage root"));
            }
            Files.write(target, content);
            return storedName;
        } catch (IOException failure) {
            throw new SystemException("Unable to store asset", failure);
        }
    }

    @Override
    public void delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        try {
            Path target = root.resolve(storedPath).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup; orphan removal is handled operationally.
        }
    }

    private String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "asset";
        }
        return fileName.replaceAll("[\\\\/]", "_").replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
