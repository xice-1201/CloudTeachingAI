package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.ResourceTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResourceTagRepository extends JpaRepository<ResourceTagEntity, Long> {

    List<ResourceTagEntity> findByResourceIdIn(Collection<Long> resourceIds);

    List<ResourceTagEntity> findAllByOrderByNormalizedLabelAscIdAsc();

    void deleteByResourceId(Long resourceId);
}
