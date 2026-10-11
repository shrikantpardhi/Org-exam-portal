package com.company.exam.content.storage;

import java.util.UUID;

/**
 * Object/file storage boundary for question assets ({@code specs/12-P3-Content-V1.5.md §41, §100}).
 *
 * <p>The provider is not hard-coded; a local implementation is used for the pilot and can be
 * replaced without changing the service.
 */
public interface AssetStorage {

    /**
     * Stores asset bytes and returns an opaque, storage-relative path.
     *
     * <p>The returned path is an implementation detail and is never exposed to a client.
     */
    String store(UUID assetId, String fileName, String contentType, byte[] content);

    /** Removes a previously stored asset. Missing files are ignored. */
    void delete(String storedPath);
}
