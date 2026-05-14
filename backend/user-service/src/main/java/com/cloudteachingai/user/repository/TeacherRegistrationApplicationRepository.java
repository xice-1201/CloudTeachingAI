package com.cloudteachingai.user.repository;

import com.cloudteachingai.user.entity.TeacherRegistrationApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRegistrationApplicationRepository extends JpaRepository<TeacherRegistrationApplication, Long> {

    Optional<TeacherRegistrationApplication> findByEmailAndStatus(String email, TeacherRegistrationApplication.Status status);

    List<TeacherRegistrationApplication> findAllByStatusOrderByRequestedAtDesc(TeacherRegistrationApplication.Status status);

    void deleteByEmailOrReviewedByOrCreatedUserId(String email, Long reviewedBy, Long createdUserId);
}
