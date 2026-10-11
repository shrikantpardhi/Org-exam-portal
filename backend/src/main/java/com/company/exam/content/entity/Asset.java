package com.company.exam.content.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * An asset belonging to exactly one QuestionVersion ({@code specs/12-P3-Content-V1.5.md §10.5, §43}).
 *
 * <p>{@code filePath} is a storage implementation detail and is never exposed to a client as an
 * authorization primitive.
 */
@Entity
@Table(name = "asset")
public class Asset extends BaseEntity {

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", columnDefinition = "text")
    private String filePath;

    @Column(name = "file_type", length = 100)
    private String fileType;

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public void setQuestionVersionId(UUID questionVersionId) {
        this.questionVersionId = questionVersionId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
