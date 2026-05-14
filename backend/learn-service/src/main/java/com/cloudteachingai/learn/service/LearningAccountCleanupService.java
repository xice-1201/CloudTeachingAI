package com.cloudteachingai.learn.service;

import com.cloudteachingai.learn.entity.AbilityTestSessionEntity;
import com.cloudteachingai.learn.repository.AbilityMapRepository;
import com.cloudteachingai.learn.repository.AbilityTestQuestionRepository;
import com.cloudteachingai.learn.repository.AbilityTestSessionRepository;
import com.cloudteachingai.learn.repository.LearningProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LearningAccountCleanupService {

    private final LearningProgressRepository learningProgressRepository;
    private final AbilityMapRepository abilityMapRepository;
    private final AbilityTestSessionRepository abilityTestSessionRepository;
    private final AbilityTestQuestionRepository abilityTestQuestionRepository;

    @Transactional
    public void deleteStudentLearningData(Long studentId) {
        List<Long> sessionIds = abilityTestSessionRepository.findByStudentId(studentId).stream()
                .map(AbilityTestSessionEntity::getId)
                .toList();
        if (!sessionIds.isEmpty()) {
            abilityTestQuestionRepository.deleteBySessionIdIn(sessionIds);
        }
        abilityTestSessionRepository.deleteByStudentId(studentId);
        abilityMapRepository.deleteByStudentId(studentId);
        learningProgressRepository.deleteByStudentId(studentId);
    }
}
