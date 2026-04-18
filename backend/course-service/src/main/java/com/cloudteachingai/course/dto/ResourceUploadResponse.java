package com.cloudteachingai.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUploadResponse {
    private String storageKey;
    private String fileName;
    private Long size;
}
