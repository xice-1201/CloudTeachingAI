package com.cloudteachingai.course.service;

import com.cloudteachingai.course.client.ResourceTagAgentClient;
import com.cloudteachingai.course.client.UserServiceClient;
import com.cloudteachingai.course.controller.CourseController.UserContext;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.dto.ResourceTagConfirmRequest;
import com.cloudteachingai.course.entity.ChapterEntity;
import com.cloudteachingai.course.entity.CourseEntity;
import com.cloudteachingai.course.entity.KnowledgePointEntity;
import com.cloudteachingai.course.entity.ResourceEntity;
import com.cloudteachingai.course.entity.ResourceKnowledgePointEntity;
import com.cloudteachingai.course.entity.ResourceTagEntity;
import com.cloudteachingai.course.entity.enums.CourseStatus;
import com.cloudteachingai.course.entity.enums.CourseVisibilityType;
import com.cloudteachingai.course.entity.enums.KnowledgePointType;
import com.cloudteachingai.course.entity.enums.ResourceStatus;
import com.cloudteachingai.course.entity.enums.ResourceTagSource;
import com.cloudteachingai.course.entity.enums.ResourceTaggingStatus;
import com.cloudteachingai.course.entity.enums.ResourceType;
import com.cloudteachingai.course.repository.ChapterRepository;
import com.cloudteachingai.course.repository.CourseRepository;
import com.cloudteachingai.course.repository.CourseVisibleStudentRepository;
import com.cloudteachingai.course.repository.EnrollmentRepository;
import com.cloudteachingai.course.repository.KnowledgePointRepository;
import com.cloudteachingai.course.repository.ResourceKnowledgePointRepository;
import com.cloudteachingai.course.repository.ResourceRepository;
import com.cloudteachingai.course.repository.ResourceTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseFacadeServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CourseVisibleStudentRepository courseVisibleStudentRepository;
    @Mock
    private KnowledgePointRepository knowledgePointRepository;
    @Mock
    private ResourceKnowledgePointRepository resourceKnowledgePointRepository;
    @Mock
    private ResourceTagRepository resourceTagRepository;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private CourseCoverStorageService courseCoverStorageService;
    @Mock
    private ResourceStorageService resourceStorageService;
    @Mock
    private ResourceTagSuggestionService resourceTagSuggestionService;
    @Mock
    private ResourceTagAgentClient resourceTagAgentClient;
    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private CourseFacadeService courseFacadeService;

    @Test
    void confirmResourceTagsPersistsTeacherReviewAndMarksResourceConfirmed() {
        ResourceEntity resource = resource();
        ChapterEntity chapter = ChapterEntity.builder()
                .id(201L)
                .courseId(301L)
                .title("函数极限")
                .orderIndex(1)
                .build();
        CourseEntity course = CourseEntity.builder()
                .id(301L)
                .teacherId(401L)
                .title("高等数学")
                .description("微积分基础")
                .status(CourseStatus.DRAFT)
                .visibilityType(CourseVisibilityType.PUBLIC)
                .build();
        KnowledgePointEntity knowledgePoint = KnowledgePointEntity.builder()
                .id(7L)
                .name("极限定义")
                .nodeType(KnowledgePointType.POINT)
                .active(true)
                .orderIndex(1)
                .build();
        ResourceTagConfirmRequest request = new ResourceTagConfirmRequest();
        request.setKnowledgePointIds(List.of(7L));
        request.setTagLabels(List.of("课堂例题"));

        when(resourceRepository.findById(1001L)).thenReturn(Optional.of(resource));
        when(chapterRepository.findById(201L)).thenReturn(Optional.of(chapter));
        when(courseRepository.findById(301L)).thenReturn(Optional.of(course));
        when(knowledgePointRepository.findByIdIn(List.of(7L))).thenReturn(List.of(knowledgePoint));
        when(knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc()).thenReturn(List.of(knowledgePoint));
        when(resourceRepository.save(any(ResourceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resourceStorageService.isManagedStorageKey("resources/1001.pdf")).thenReturn(false);
        when(resourceKnowledgePointRepository.findByResourceIdIn(anyCollection())).thenReturn(List.of());
        when(resourceTagRepository.findByResourceIdIn(anyCollection())).thenReturn(List.of());

        ResourceResponse response = courseFacadeService.confirmResourceTags(
                1001L,
                request,
                new UserContext(401L, "TEACHER")
        );

        verify(resourceKnowledgePointRepository).deleteByResourceId(1001L);
        verify(resourceTagRepository).deleteByResourceId(1001L);

        ArgumentCaptor<Iterable<ResourceKnowledgePointEntity>> relationCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(resourceKnowledgePointRepository).saveAll(relationCaptor.capture());
        List<ResourceKnowledgePointEntity> relations = toList(relationCaptor.getValue());
        assertThat(relations)
                .singleElement()
                .satisfies(relation -> {
                    assertThat(relation.getResourceId()).isEqualTo(1001L);
                    assertThat(relation.getKnowledgePointId()).isEqualTo(7L);
                    assertThat(relation.getConfidence()).isEqualTo(1D);
                    assertThat(relation.getSource()).isEqualTo(ResourceTagSource.MANUAL);
                });

        ArgumentCaptor<Iterable<ResourceTagEntity>> tagCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(resourceTagRepository).saveAll(tagCaptor.capture());
        List<ResourceTagEntity> tags = toList(tagCaptor.getValue());
        assertThat(tags)
                .extracting(ResourceTagEntity::getLabel)
                .containsExactly("课堂例题", "极限定义");
        assertThat(tags).allSatisfy(tag -> assertThat(tag.getSource()).isEqualTo(ResourceTagSource.MANUAL));

        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.PUBLISHED);
        assertThat(resource.getTaggingStatus()).isEqualTo(ResourceTaggingStatus.CONFIRMED);
        assertThat(resource.getTaggingUpdatedAt()).isNotNull();
        assertThat(response.getTaggingStatus()).isEqualTo("CONFIRMED");
    }

    private ResourceEntity resource() {
        return ResourceEntity.builder()
                .id(1001L)
                .chapterId(201L)
                .title("函数极限讲义")
                .type(ResourceType.DOCUMENT)
                .storageKey("resources/1001.pdf")
                .description("包含函数极限的定义和例题")
                .status(ResourceStatus.PROCESSING)
                .taggingStatus(ResourceTaggingStatus.SUGGESTED)
                .taggingUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .orderIndex(1)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .build();
    }

    private <T> List<T> toList(Iterable<T> values) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }
}
