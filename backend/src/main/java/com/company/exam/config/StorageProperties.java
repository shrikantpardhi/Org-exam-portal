package com.company.exam.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * File-storage configuration names.
 *
 * <p>P0 establishes the configuration boundary only. Actual upload policy enforcement is
 * implemented with the Content module ({@code specs/09-P0-Foundation-V1.5.md §56}); the storage
 * abstraction is local in development and S3-compatible in production
 * ({@code specs/06-Architecture-Freeze-V1.5.md §68}).
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    public enum Provider {
        LOCAL,
        S3
    }

    /** Active storage provider. */
    private Provider provider = Provider.LOCAL;

    /** Root directory for local development storage. Never served as static content. */
    private String localRoot = "./data/uploads";

    /** Maximum accepted upload size. */
    private DataSize maxFileSize = DataSize.ofMegabytes(25);

    /** Allowed content types for uploads. */
    private List<String> allowedContentTypes = List.of("image/png", "image/jpeg", "application/pdf");

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }
}
