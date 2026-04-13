package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<ResourceEntity, Long> {

    List<ResourceEntity> findByChapterIdOrderByOrderIndexAscIdAsc(Long chapterId);

    Optional<ResourceEntity> findTopByChapterIdOrderByOrderIndexDescIdDesc(Long chapterId);
}
