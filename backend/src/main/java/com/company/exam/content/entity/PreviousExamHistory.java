package com.company.exam.content.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A historical occurrence of a question ({@code specs/12-P3-Content-V1.5.md §10.8, §45}).
 *
 * <p>Multiple rows per question are allowed; occurrences are not compressed into one year.
 */
@Entity
@Table(name = "previous_exam_history")
public class PreviousExamHistory extends BaseEntity {

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "year")
    private Integer year;

    @Column(name = "shift", length = 50)
    private String shift;

    @Column(name = "source", length = 100)
    private String source;

    public UUID getQuestionId() {
        return questionId;
    }

    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
    }

    public UUID getExamId() {
        return examId;
    }

    public void setExamId(UUID examId) {
        this.examId = examId;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
