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
public class ExerciseGenerateResponse {
    private String title;
    private String description;
    private List<ResourceResponse.ExerciseQuestionResponse> questions;
}
