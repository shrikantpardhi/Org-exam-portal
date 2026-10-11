package com.company.exam.content.rendering;

import com.company.exam.content.repository.AssetRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Verifies asset shortcode references ({@code specs/12-P3-Content-V1.5.md §15, §43, §52}).
 *
 * <p>An asset must exist and belong to the same QuestionVersion as the content; a cross-version
 * reference returns {@code ASSET_VERSION_MISMATCH}. Raw file paths are never used as authorization
 * primitives.
 */
@Component
public class AssetReferenceResolver {

    private final AssetRepository assetRepository;

    public AssetReferenceResolver(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /** Verifies each referenced asset exists and belongs to {@code questionVersionId}. */
    public void verifyOwnership(UUID questionVersionId, List<UUID> assetIds) {
        for (UUID assetId : assetIds) {
            var asset = assetRepository.findById(assetId).orElse(null);
            if (asset == null) {
                throw ContentValidationException.of("content", ContentErrorCode.ASSET_NOT_FOUND,
                        "Referenced asset does not exist: " + assetId);
            }
            if (!asset.getQuestionVersionId().equals(questionVersionId)) {
                throw ContentValidationException.of("content", ContentErrorCode.ASSET_VERSION_MISMATCH,
                        "Referenced asset belongs to a different question version");
            }
        }
    }
}
