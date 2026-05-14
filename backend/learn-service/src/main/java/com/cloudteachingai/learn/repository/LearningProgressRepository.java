package com.cloudteachingai.learn.repository;

import com.cloudteachingai.learn.entity.LearningProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningProgressRepository extends JpaRepository<LearningProgressEntity, Long> {

    Optional<LearningProgressEntity> findByStudentIdAndResourceId(Long studentId, Long resourceId);

    List<LearningProgressEntity> findByStudentIdAndCourseId(Long studentId, Long courseId);

    List<LearningProgressEntity> findByStudentId(Long studentId);

    void deleteByStudentId(Long studentId);

    List<LearningProgressEntity> findByCourseIdIn(List<Long> courseIds);
}
