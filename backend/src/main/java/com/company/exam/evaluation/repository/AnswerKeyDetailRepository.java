package com.company.exam.evaluation.repository;

import com.company.exam.evaluation.entity.AnswerKeyDetail;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AnswerKeyDetail} ({@code specs/15-P6-Evaluation-V1.5.md §5.2, §128}). */
public interface AnswerKeyDetailRepository extends JpaRepository<AnswerKeyDetail, UUID> {

    List<AnswerKeyDetail> findByAnswerKeyId(UUID answerKeyId);

    Optional<AnswerKeyDetail> findByIdAndAnswerKeyId(UUID id, UUID answerKeyId);
}
