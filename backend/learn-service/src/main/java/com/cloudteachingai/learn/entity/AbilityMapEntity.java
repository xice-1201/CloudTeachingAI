package com.cloudteachingai.learn.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ability_map")
public class AbilityMapEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(name = "knowledge_point_name", nullable = false)
    private String knowledgePointName;

    @Column(name = "knowledge_point_path")
    private String knowledgePointPath;

    @Column(name = "mastery_level", nullable = false)
    private Double masteryLevel;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "test_score", nullable = false)
    private Double testScore;

    @Column(name = "progress_score", nullable = false)
    private Double progressScore;

    @Column(name = "resource_count", nullable = false)
    private Integer resourceCount;

    @Column(nullable = false)
    private String source;

    @Column(name = "last_tested_at")
    private OffsetDateTime lastTestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
        confidence = confidence == null ? 0D : confidence;
        testScore = testScore == null ? 0D : testScore;
        progressScore = progressScore == null ? 0D : progressScore;
        resourceCount = resourceCount == null ? 0 : resourceCount;
        masteryLevel = masteryLevel == null ? 0D : masteryLevel;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
