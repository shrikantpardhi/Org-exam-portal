package com.company.exam.evaluation.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The derived presentation artifact for one result ({@code specs/15-P6-Evaluation-V1.5.md §5.5, §57-§61}).
 *
 * <p>{@code UNIQUE(result_id)} means one scorecard per result; regeneration is business-idempotent.
 * {@code filePath} is a private storage reference and is never returned to a caller ({@code §58}).
 */
@Entity
@Table(name = "scorecard")
public class Scorecard extends BaseEntity {

    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @Column(name = "generated_on")
    private Instant generatedOn;

    @Column(name = "file_path", columnDefinition = "text")
    private String filePath;

    public UUID getResultId() {
        return resultId;
    }

    public void setResultId(UUID resultId) {
        this.resultId = resultId;
    }

    public Instant getGeneratedOn() {
        return generatedOn;
    }

    public void setGeneratedOn(Instant generatedOn) {
        this.generatedOn = generatedOn;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
