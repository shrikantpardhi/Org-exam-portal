package com.company.exam.content.service;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.content.config.ContentProperties;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.Asset;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.repository.AssetRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import com.company.exam.content.storage.AssetStorage;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Asset workflows ({@code specs/12-P3-Content-V1.5.md §41-§43, §77, §100}).
 *
 * <p>Validates type/size, stores the object, persists metadata, and never exposes the storage path.
 * Published-version immutability applies.
 */
@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final QuestionVersionRepository versionRepository;
    private final ContentGuards guards;
    private final AssetStorage storage;
    private final ContentProperties properties;
    private final ContentMapper mapper;
    private final SecurityEventLogger audit;

    public AssetService(AssetRepository assetRepository, QuestionVersionRepository versionRepository,
            ContentGuards guards, AssetStorage storage, ContentProperties properties, ContentMapper mapper,
            SecurityEventLogger audit) {
        this.assetRepository = assetRepository;
        this.versionRepository = versionRepository;
        this.guards = guards;
        this.storage = storage;
        this.properties = properties;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.AssetResponse> list(UUID versionId) {
        requireVersion(versionId);
        return assetRepository.findByQuestionVersionIdOrderByCreatedOnAsc(versionId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional
    public ContentDtos.AssetResponse upload(UUID versionId, MultipartFile file, UUID actorId) {
        QuestionVersion version = requireMutableVersion(versionId);
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("file", "EMPTY_FILE", "The uploaded file is empty")));
        }
        if (file.getSize() > properties.getMaxAssetSizeBytes()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("file", "FILE_TOO_LARGE", "The uploaded file exceeds the maximum size")));
        }
        String contentType = file.getContentType();
        if (contentType == null || !properties.getAllowedAssetTypes().contains(contentType)) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("file", "UNSUPPORTED_FILE_TYPE", "This file type is not allowed")));
        }

        UUID storageKey = UUID.randomUUID();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException failure) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("file", "UNREADABLE_FILE", "The uploaded file could not be read")));
        }
        String storedPath = storage.store(storageKey, file.getOriginalFilename(), contentType, bytes);

        Asset asset = new Asset();
        asset.setQuestionVersionId(version.getId());
        asset.setFileName(file.getOriginalFilename());
        asset.setFilePath(storedPath);
        asset.setFileType(contentType);
        asset.setStatus(Status.ACTIVE);
        try {
            Asset saved = assetRepository.saveAndFlush(asset);
            audit.audit(actorId, "UPLOAD", "Asset", saved.getId(), "Uploaded asset to version " + version.getId());
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException failure) {
            // Recoverable pattern: persistence failed after the object was stored; remove the orphan.
            storage.delete(storedPath);
            throw failure;
        }
    }

    @Transactional
    public void delete(UUID versionId, UUID assetId, UUID actorId) {
        requireMutableVersion(versionId);
        Asset asset = assetRepository.findByIdAndQuestionVersionId(assetId, versionId)
                .orElseThrow(() -> NotFoundException.of("Asset", assetId));
        assetRepository.delete(asset);
        storage.delete(asset.getFilePath());
        audit.audit(actorId, "DELETE", "Asset", assetId, "Removed asset from version " + versionId);
    }

    private QuestionVersion requireVersion(UUID versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Question version", versionId));
    }

    private QuestionVersion requireMutableVersion(UUID versionId) {
        QuestionVersion version = requireVersion(versionId);
        guards.requireMutable(version);
        return version;
    }
}
