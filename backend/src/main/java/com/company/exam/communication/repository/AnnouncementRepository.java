package com.company.exam.communication.repository;

import com.company.exam.communication.entity.Announcement;
import com.company.exam.common.enums.Status;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link Announcement} ({@code specs/19-P10-Communication-V1.5.md §7.3, §42}). */
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID>,
        JpaSpecificationExecutor<Announcement> {

    List<Announcement> findByStatus(Status status);
}
