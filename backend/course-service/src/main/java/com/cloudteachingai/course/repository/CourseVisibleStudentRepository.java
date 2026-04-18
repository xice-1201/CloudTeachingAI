package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.CourseVisibleStudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CourseVisibleStudentRepository extends JpaRepository<CourseVisibleStudentEntity, Long> {

    List<CourseVisibleStudentEntity> findByCourseId(Long courseId);

    List<CourseVisibleStudentEntity> findByCourseIdIn(Collection<Long> courseIds);

    boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

    long countByCourseId(Long courseId);

    void deleteByCourseId(Long courseId);
}
