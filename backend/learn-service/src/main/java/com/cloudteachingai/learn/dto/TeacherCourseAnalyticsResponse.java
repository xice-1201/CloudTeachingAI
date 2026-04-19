package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeacherCourseAnalyticsResponse {
    Long courseId;
    String courseTitle;
    String courseStatus;
    Integer totalResources;
    Integer activeStudents;
    Double averageProgress;
    Double completionRate;
    Integer learningRecordCount;
    String hottestResourceTitle;
    Integer hottestResourceLearningCount;
    String lastLearnedAt;
}
