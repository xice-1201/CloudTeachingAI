package com.cloudteachingai.learn.repository;

import com.cloudteachingai.learn.entity.AbilityTestSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface AbilityTestSessionRepository extends JpaRepository<AbilityTestSessionEntity, Long> {

    Optional<AbilityTestSessionEntity> findByIdAndStudentId(Long id, Long studentId);

    List<AbilityTestSessionEntity> findByStudentId(Long studentId);

    void deleteByStudentId(Long studentId);
}
