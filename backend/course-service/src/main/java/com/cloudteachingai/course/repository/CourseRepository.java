package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface CourseRepository extends JpaRepository<CourseEntity, Long>, JpaSpecificationExecutor<CourseEntity> {

    List<CourseEntity> findAllByIdInOrderByUpdatedAtDesc(Collection<Long> ids);

    List<CourseEntity> findByTeacherId(Long teacherId);
}
