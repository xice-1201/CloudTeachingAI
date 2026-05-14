package com.cloudteachingai.assign.repository;

import com.cloudteachingai.assign.entity.AssignmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long> {

    Page<AssignmentEntity> findByCourseIdOrderByDeadlineAscIdDesc(Long courseId, Pageable pageable);

    List<AssignmentEntity> findByCourseIdInAndDeadlineAfterOrderByDeadlineAscIdAsc(Collection<Long> courseIds, OffsetDateTime deadline);

    List<AssignmentEntity> findByCourseIdInOrderByDeadlineAscIdDesc(Collection<Long> courseIds);

    List<AssignmentEntity> findByTeacherId(Long teacherId);

    void deleteByTeacherId(Long teacherId);
}
