package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.CourseDiscussionPostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CourseDiscussionPostRepository extends JpaRepository<CourseDiscussionPostEntity, Long> {

    List<CourseDiscussionPostEntity> findByCourseIdAndParentIdIsNullOrderByCreatedAtDescIdDesc(Long courseId);

    List<CourseDiscussionPostEntity> findByCourseIdAndResourceIdAndParentIdIsNullOrderByCreatedAtDescIdDesc(Long courseId, Long resourceId);

    List<CourseDiscussionPostEntity> findByParentIdInOrderByCreatedAtAscIdAsc(Collection<Long> parentIds);

    void deleteByAuthorId(Long authorId);
}
