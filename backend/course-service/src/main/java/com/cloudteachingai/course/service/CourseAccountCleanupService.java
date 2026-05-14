package com.cloudteachingai.course.service;

import com.cloudteachingai.course.entity.CourseEntity;
import com.cloudteachingai.course.entity.ResourceEntity;
import com.cloudteachingai.course.repository.ChapterRepository;
import com.cloudteachingai.course.repository.CourseAnnouncementRepository;
import com.cloudteachingai.course.repository.CourseDiscussionPostRepository;
import com.cloudteachingai.course.repository.CourseRepository;
import com.cloudteachingai.course.repository.CourseVisibleStudentRepository;
import com.cloudteachingai.course.repository.EnrollmentRepository;
import com.cloudteachingai.course.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseAccountCleanupService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final ResourceRepository resourceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseVisibleStudentRepository courseVisibleStudentRepository;
    private final CourseDiscussionPostRepository courseDiscussionPostRepository;
    private final CourseAnnouncementRepository courseAnnouncementRepository;
    private final ResourceStorageService resourceStorageService;
    private final CourseCoverStorageService courseCoverStorageService;

    @Transactional
    public void deleteStudentCourseData(Long studentId) {
        enrollmentRepository.deleteByStudentId(studentId);
        courseVisibleStudentRepository.deleteByStudentId(studentId);
        courseDiscussionPostRepository.deleteByAuthorId(studentId);
    }

    @Transactional
    public void deleteTeacherCourseData(Long teacherId) {
        courseAnnouncementRepository.deleteByAuthorId(teacherId);
        courseDiscussionPostRepository.deleteByAuthorId(teacherId);
        List<CourseEntity> courses = courseRepository.findByTeacherId(teacherId);
        for (CourseEntity course : courses) {
            deleteManagedResourcesForCourse(course.getId());
            courseCoverStorageService.deleteIfManaged(course.getCoverKey());
        }
        courseRepository.deleteAll(courses);
    }

    private void deleteManagedResourcesForCourse(Long courseId) {
        List<Long> chapterIds = chapterRepository.findByCourseIdOrderByOrderIndexAscIdAsc(courseId).stream()
                .map(chapter -> chapter.getId())
                .toList();
        if (chapterIds.isEmpty()) {
            return;
        }
        resourceRepository.findByChapterIdInOrderByOrderIndexAscIdAsc(chapterIds).stream()
                .map(ResourceEntity::getStorageKey)
                .forEach(resourceStorageService::deleteIfManaged);
    }
}
