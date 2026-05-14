package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.enums.CourseStatus;
import com.cloudteachingai.course.entity.EnrollmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    Optional<EnrollmentEntity> findByStudentIdAndCourseId(Long studentId, Long courseId);

    Page<EnrollmentEntity> findByStudentId(Long studentId, Pageable pageable);

    List<EnrollmentEntity> findByCourseId(Long courseId);

    void deleteByStudentId(Long studentId);

    @Query(
            value = """
                    SELECT e
                    FROM EnrollmentEntity e, CourseEntity c
                    WHERE e.courseId = c.id
                      AND e.studentId = :studentId
                      AND c.status = :status
                    ORDER BY e.enrolledAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(e)
                    FROM EnrollmentEntity e, CourseEntity c
                    WHERE e.courseId = c.id
                      AND e.studentId = :studentId
                      AND c.status = :status
                    """
    )
    Page<EnrollmentEntity> findByStudentIdAndCourseStatus(
            @Param("studentId") Long studentId,
            @Param("status") CourseStatus status,
            Pageable pageable
    );
}
