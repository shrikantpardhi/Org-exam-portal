package com.company.exam.content.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Content rendering and asset limits ({@code specs/12-P3-Content-V1.5.md §60, §41}).
 *
 * <p>Limits are explicitly configured technical bounds, not silently invented business rules.
 */
@ConfigurationProperties(prefix = "app.content")
public class ContentProperties {

    /** Maximum canonical content length, in characters. */
    private int maxContentLength = 100_000;

    /** Maximum asset upload size, in bytes. */
    private long maxAssetSizeBytes = 5_000_000;

    /** Allowed asset content types. */
    private List<String> allowedAssetTypes = List.of("image/png", "image/jpeg", "image/gif", "image/webp");

    /** Local asset storage root (the object-storage provider is not hard-coded). */
    private String assetStorageRoot = "./data/content-assets";

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public long getMaxAssetSizeBytes() {
        return maxAssetSizeBytes;
    }

    public void setMaxAssetSizeBytes(long maxAssetSizeBytes) {
        this.maxAssetSizeBytes = maxAssetSizeBytes;
    }

    public List<String> getAllowedAssetTypes() {
        return allowedAssetTypes;
    }

    public void setAllowedAssetTypes(List<String> allowedAssetTypes) {
        this.allowedAssetTypes = allowedAssetTypes;
    }

    public String getAssetStorageRoot() {
        return assetStorageRoot;
    }

    public void setAssetStorageRoot(String assetStorageRoot) {
        this.assetStorageRoot = assetStorageRoot;
    }
}
