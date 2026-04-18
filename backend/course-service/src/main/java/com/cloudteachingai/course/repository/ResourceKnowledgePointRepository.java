package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.ResourceKnowledgePointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResourceKnowledgePointRepository extends JpaRepository<ResourceKnowledgePointEntity, Long> {

    List<ResourceKnowledgePointEntity> findByResourceIdIn(Collection<Long> resourceIds);

    void deleteByResourceId(Long resourceId);
}
