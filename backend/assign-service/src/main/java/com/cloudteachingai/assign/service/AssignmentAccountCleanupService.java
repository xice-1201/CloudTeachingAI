package com.cloudteachingai.assign.service;

import com.cloudteachingai.assign.entity.AssignmentEntity;
import com.cloudteachingai.assign.repository.AssignmentRepository;
import com.cloudteachingai.assign.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentAccountCleanupService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    @Transactional
    public void deleteStudentAssignmentData(Long studentId) {
        submissionRepository.deleteByStudentId(studentId);
    }

    @Transactional
    public void deleteTeacherAssignmentData(Long teacherId) {
        List<Long> assignmentIds = assignmentRepository.findByTeacherId(teacherId).stream()
                .map(AssignmentEntity::getId)
                .toList();
        if (!assignmentIds.isEmpty()) {
            submissionRepository.deleteByAssignmentIdIn(assignmentIds);
        }
        assignmentRepository.deleteByTeacherId(teacherId);
    }
}
