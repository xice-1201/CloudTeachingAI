package com.cloudteachingai.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {
    private Long id;
    private String title;
    private String description;
    private String coverImage;
    private Long teacherId;
    private String teacherName;
    private String status;
    private String visibilityType;
    private List<Long> visibleStudentIds;
    private Integer visibleStudentCount;
    private String createdAt;
    private String updatedAt;
}
