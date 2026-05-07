package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeacherStudentRiskResponse {
    Long courseId;
    String courseTitle;
    Integer activeStudents;
    Integer lowProgressStudents;
    Integer inactiveStudents;
    Integer completedStudents;
    String riskLevel;
    String insight;
}
