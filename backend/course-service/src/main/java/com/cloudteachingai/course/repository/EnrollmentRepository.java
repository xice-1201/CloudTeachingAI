package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.EnrollmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    Optional<EnrollmentEntity> findByStudentIdAndCourseId(Long studentId, Long courseId);

    Page<EnrollmentEntity> findByStudentId(Long studentId, Pageable pageable);
}
