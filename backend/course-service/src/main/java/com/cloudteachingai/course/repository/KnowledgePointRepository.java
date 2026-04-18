package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.KnowledgePointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePointEntity, Long> {

    List<KnowledgePointEntity> findAllByOrderByOrderIndexAscIdAsc();

    List<KnowledgePointEntity> findByActiveTrueOrderByOrderIndexAscIdAsc();

    List<KnowledgePointEntity> findByIdIn(Collection<Long> ids);
}
