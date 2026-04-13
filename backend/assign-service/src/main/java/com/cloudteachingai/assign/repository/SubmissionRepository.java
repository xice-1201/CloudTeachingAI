package com.cloudteachingai.assign.repository;

import com.cloudteachingai.assign.entity.SubmissionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

    Optional<SubmissionEntity> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    Page<SubmissionEntity> findByAssignmentIdOrderBySubmittedAtDescIdDesc(Long assignmentId, Pageable pageable);

    List<SubmissionEntity> findByStudentIdAndAssignmentIdIn(Long studentId, Collection<Long> assignmentIds);

    boolean existsByAssignmentIdAndStudentId(Long assignmentId, Long studentId);
}
