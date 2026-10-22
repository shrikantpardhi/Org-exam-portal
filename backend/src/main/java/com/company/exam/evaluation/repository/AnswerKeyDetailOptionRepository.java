package com.company.exam.evaluation.repository;

import com.company.exam.evaluation.entity.AnswerKeyDetailOption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerKeyDetailOptionRepository extends JpaRepository<AnswerKeyDetailOption, UUID> {

    List<AnswerKeyDetailOption> findByAnswerKeyDetailId(UUID answerKeyDetailId);

    void deleteByAnswerKeyDetailId(UUID answerKeyDetailId);
}