package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.CourseAnnouncementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncementEntity, Long> {

    List<CourseAnnouncementEntity> findByCourseIdOrderByPinnedDescPublishedAtDescIdDesc(Long courseId);

    void deleteByAuthorId(Long authorId);
}
