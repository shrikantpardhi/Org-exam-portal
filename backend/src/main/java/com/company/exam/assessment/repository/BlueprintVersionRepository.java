package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.BlueprintVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link BlueprintVersion} ({@code §5.2, §22}). */
public interface BlueprintVersionRepository extends JpaRepository<BlueprintVersion, UUID> {

    List<BlueprintVersion> findByBlueprintIdOrderByVersionAsc(UUID blueprintId);

    Optional<BlueprintVersion> findByIdAndBlueprintId(UUID id, UUID blueprintId);

    Optional<BlueprintVersion> findByBlueprintIdAndVersion(UUID blueprintId, Integer version);

    boolean existsByBlueprintIdAndVersion(UUID blueprintId, Integer version);

    @Query("select coalesce(max(v.version), 0) from BlueprintVersion v where v.blueprintId = :blueprintId")
    int findMaxVersion(@Param("blueprintId") UUID blueprintId);
}
