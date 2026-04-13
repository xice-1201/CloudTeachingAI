package com.cloudteachingai.assign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {
    private Long id;
    private Long courseId;
    private String courseTitle;
    private Long teacherId;
    private String title;
    private String description;
    private String gradingCriteria;
    private String submitType;
    private Double maxScore;
    private String dueDate;
    private String createdAt;
}
