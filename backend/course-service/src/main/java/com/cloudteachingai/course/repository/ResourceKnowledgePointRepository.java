package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.ResourceKnowledgePointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ResourceKnowledgePointRepository extends JpaRepository<ResourceKnowledgePointEntity, Long> {

    List<ResourceKnowledgePointEntity> findByResourceIdIn(Collection<Long> resourceIds);

    void deleteByResourceId(Long resourceId);

    @Query("""
            select relation.knowledgePointId as knowledgePointId, count(distinct relation.resourceId) as resourceCount
            from ResourceKnowledgePointEntity relation
            group by relation.knowledgePointId
            """)
    List<ResourceCountProjection> countResourcesByKnowledgePoint();

    @Query("""
            select relation.resourceId as resourceId, relation.knowledgePointId as knowledgePointId
            from ResourceKnowledgePointEntity relation
            """)
    List<ResourceKnowledgePointLinkProjection> findResourceKnowledgePointLinks();

    interface ResourceCountProjection {
        Long getKnowledgePointId();

        Long getResourceCount();
    }

    interface ResourceKnowledgePointLinkProjection {
        Long getResourceId();

        Long getKnowledgePointId();
    }
}
