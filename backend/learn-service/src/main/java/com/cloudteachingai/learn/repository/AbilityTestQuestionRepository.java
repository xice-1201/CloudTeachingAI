package com.cloudteachingai.learn.repository;

import com.cloudteachingai.learn.entity.AbilityTestQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AbilityTestQuestionRepository extends JpaRepository<AbilityTestQuestionEntity, Long> {

    List<AbilityTestQuestionEntity> findBySessionIdOrderByDisplayOrderAsc(Long sessionId);

    Optional<AbilityTestQuestionEntity> findByIdAndSessionId(Long id, Long sessionId);

    void deleteBySessionIdIn(List<Long> sessionIds);
}
