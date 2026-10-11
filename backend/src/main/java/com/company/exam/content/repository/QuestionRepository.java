package com.company.exam.content.repository;

import com.company.exam.content.entity.Question;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link Question} ({@code specs/12-P3-Content-V1.5.md §88}). */
public interface QuestionRepository extends JpaRepository<Question, UUID>, JpaSpecificationExecutor<Question> {

    List<Question> findAllByIdIn(Collection<UUID> ids);
}
