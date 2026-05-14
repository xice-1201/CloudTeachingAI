package com.cloudteachingai.learn.repository;

import com.cloudteachingai.learn.entity.AbilityMapEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AbilityMapRepository extends JpaRepository<AbilityMapEntity, Long> {

    List<AbilityMapEntity> findByStudentIdOrderByMasteryLevelDescUpdatedAtDesc(Long studentId);

    Optional<AbilityMapEntity> findByStudentIdAndKnowledgePointId(Long studentId, Long knowledgePointId);

    void deleteByStudentId(Long studentId);
}
