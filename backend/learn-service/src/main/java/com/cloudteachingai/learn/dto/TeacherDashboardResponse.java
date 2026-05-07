package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TeacherDashboardResponse {
    Integer totalCourses;
    Integer publishedCourses;
    Integer totalResources;
    Integer activeStudents;
    Double averageProgress;
    List<TeacherCourseAnalyticsResponse> courses;
    List<TeacherKnowledgePointAnalyticsResponse> weakKnowledgePoints;
    List<TeacherStudentRiskResponse> studentRisks;
}
