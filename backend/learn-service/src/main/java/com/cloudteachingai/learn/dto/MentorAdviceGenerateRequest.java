package com.cloudteachingai.learn.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MentorAdviceGenerateRequest {
    @Size(max = 100)
    private String studentName;

    @Size(max = 1000)
    private String teacherInstruction;
}
