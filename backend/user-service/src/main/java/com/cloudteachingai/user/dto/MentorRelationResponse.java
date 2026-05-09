package com.cloudteachingai.user.dto;

import com.cloudteachingai.user.entity.MentorRelation;
import com.cloudteachingai.user.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MentorRelationResponse {
    private Long id;
    private Long studentId;
    private Long mentorId;
    private String status;
    private UserResponse student;
    private UserResponse mentor;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private String reviewNote;

    public static MentorRelationResponse from(MentorRelation relation, User student, User mentor) {
        return MentorRelationResponse.builder()
                .id(relation.getId())
                .studentId(relation.getStudentId())
                .mentorId(relation.getMentorId())
                .status(relation.getStatus().name())
                .student(student == null ? null : UserResponse.from(student))
                .mentor(mentor == null ? null : UserResponse.from(mentor))
                .requestedAt(relation.getRequestedAt())
                .reviewedAt(relation.getReviewedAt())
                .reviewNote(relation.getReviewNote())
                .build();
    }
}
