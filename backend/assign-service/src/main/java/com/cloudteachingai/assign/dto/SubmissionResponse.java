package com.cloudteachingai.assign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String content;
    private List<String> attachments;
    private Double aiScore;
    private String aiFeedback;
    private Double finalScore;
    private String finalFeedback;
    private Double score;
    private String feedback;
    private String status;
    private String submittedAt;
    private String gradedAt;
}
