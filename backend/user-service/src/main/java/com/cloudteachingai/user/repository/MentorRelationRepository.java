package com.cloudteachingai.user.repository;

import com.cloudteachingai.user.entity.MentorRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MentorRelationRepository extends JpaRepository<MentorRelation, Long> {

    Optional<MentorRelation> findByIdAndMentorId(Long id, Long mentorId);

    Optional<MentorRelation> findByStudentIdAndMentorIdAndStatus(
            Long studentId,
            Long mentorId,
            MentorRelation.Status status);

    Optional<MentorRelation> findFirstByStudentIdAndStatusOrderByRequestedAtDesc(
            Long studentId,
            MentorRelation.Status status);

    boolean existsByStudentIdAndStatus(Long studentId, MentorRelation.Status status);

    List<MentorRelation> findAllByStudentIdAndStatusOrderByRequestedAtDesc(
            Long studentId,
            MentorRelation.Status status);

    List<MentorRelation> findAllByMentorIdAndStatusOrderByRequestedAtDesc(
            Long mentorId,
            MentorRelation.Status status);

    List<MentorRelation> findAllByMentorIdAndStatusOrderByReviewedAtDesc(
            Long mentorId,
            MentorRelation.Status status);

    void deleteByStudentIdOrMentorId(Long studentId, Long mentorId);
}
