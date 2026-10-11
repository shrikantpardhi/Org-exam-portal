package com.company.exam.content.repository;

import com.company.exam.content.entity.Asset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Asset} ({@code specs/12-P3-Content-V1.5.md §41, §43}). */
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByQuestionVersionIdOrderByCreatedOnAsc(UUID questionVersionId);

    Optional<Asset> findByIdAndQuestionVersionId(UUID id, UUID questionVersionId);

    boolean existsByIdAndQuestionVersionId(UUID id, UUID questionVersionId);
}
