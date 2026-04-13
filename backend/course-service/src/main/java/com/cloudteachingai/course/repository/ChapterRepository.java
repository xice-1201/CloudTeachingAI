package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.ChapterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<ChapterEntity, Long> {

    List<ChapterEntity> findByCourseIdOrderByOrderIndexAscIdAsc(Long courseId);

    Optional<ChapterEntity> findTopByCourseIdOrderByOrderIndexDescIdDesc(Long courseId);
}
