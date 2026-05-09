package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.ResourceTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ResourceTagRepository extends JpaRepository<ResourceTagEntity, Long> {

    List<ResourceTagEntity> findByResourceIdIn(Collection<Long> resourceIds);

    List<ResourceTagEntity> findAllByOrderByNormalizedLabelAscIdAsc();

    void deleteByResourceId(Long resourceId);

    @Query("""
            select tag.resourceId as resourceId,
                   tag.knowledgePointId as knowledgePointId,
                   tag.label as label,
                   tag.normalizedLabel as normalizedLabel
            from ResourceTagEntity tag
            """)
    List<ResourceTagKnowledgeLinkProjection> findResourceTagKnowledgeLinks();

    interface ResourceTagKnowledgeLinkProjection {
        Long getResourceId();

        Long getKnowledgePointId();

        String getLabel();

        String getNormalizedLabel();
    }
}
