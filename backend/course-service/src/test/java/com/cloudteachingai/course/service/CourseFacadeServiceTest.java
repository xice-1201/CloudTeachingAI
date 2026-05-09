package com.cloudteachingai.course.service;

import com.cloudteachingai.course.client.ResourceTagAgentClient;
import com.cloudteachingai.course.client.UserServiceClient;
import com.cloudteachingai.course.controller.CourseController.UserContext;
import com.cloudteachingai.course.dto.ExerciseGenerateRequest;
import com.cloudteachingai.course.dto.ExerciseGenerateResponse;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.dto.ResourceTagConfirmRequest;
import com.cloudteachingai.course.dto.ResourceUpsertRequest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
    private ExerciseQuestionGenerationService exerciseQuestionGenerationService;
    @Mock
    private ResourceTagAgentClient resourceTagAgentClient;
    @Mock
    private OutboxService outboxService;
    @Mock
    private PlatformTransactionManager transactionManager;

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
        request.setTagLabels(List.of());

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
                .containsExactly("极限定义");
        assertThat(tags).allSatisfy(tag -> assertThat(tag.getSource()).isEqualTo(ResourceTagSource.MANUAL));

        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.PUBLISHED);
        assertThat(resource.getTaggingStatus()).isEqualTo(ResourceTaggingStatus.CONFIRMED);
        assertThat(resource.getTaggingUpdatedAt()).isNotNull();
        assertThat(response.getTaggingStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmResourceTagsCreatesMissingManualTagUnderSelectedDomain() {
        ResourceEntity resource = resource();
        ChapterEntity chapter = ChapterEntity.builder()
                .id(201L)
                .courseId(301L)
                .title("Python")
                .orderIndex(1)
                .build();
        CourseEntity course = CourseEntity.builder()
                .id(301L)
                .teacherId(401L)
                .title("Python")
                .description("Python")
                .status(CourseStatus.DRAFT)
                .visibilityType(CourseVisibilityType.PUBLIC)
                .build();
        KnowledgePointEntity subject = KnowledgePointEntity.builder()
                .id(1L)
                .name("计算机科学")
                .nodeType(KnowledgePointType.SUBJECT)
                .active(true)
                .orderIndex(1)
                .build();
        KnowledgePointEntity python = KnowledgePointEntity.builder()
                .id(2L)
                .parentId(1L)
                .name("Python")
                .nodeType(KnowledgePointType.DOMAIN)
                .active(true)
                .orderIndex(1)
                .build();
        ResourceTagConfirmRequest request = new ResourceTagConfirmRequest();
        request.setKnowledgePointIds(List.of(2L));
        request.setTagLabels(List.of("工具"));

        when(resourceRepository.findById(1001L)).thenReturn(Optional.of(resource));
        when(chapterRepository.findById(201L)).thenReturn(Optional.of(chapter));
        when(courseRepository.findById(301L)).thenReturn(Optional.of(course));
        when(knowledgePointRepository.findByIdIn(List.of(2L))).thenReturn(List.of(python));
        when(knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc()).thenReturn(new ArrayList<>(List.of(subject, python)));
        when(knowledgePointRepository.findAllByOrderByOrderIndexAscIdAsc()).thenReturn(new ArrayList<>(List.of(subject, python)));
        when(knowledgePointRepository.save(any(KnowledgePointEntity.class))).thenAnswer(invocation -> {
            KnowledgePointEntity entity = invocation.getArgument(0);
            entity.setId(8L);
            return entity;
        });
        when(resourceRepository.save(any(ResourceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resourceStorageService.isManagedStorageKey("resources/1001.pdf")).thenReturn(false);
        when(resourceKnowledgePointRepository.findByResourceIdIn(anyCollection())).thenReturn(List.of());
        when(resourceTagRepository.findByResourceIdIn(anyCollection())).thenReturn(List.of());

        courseFacadeService.confirmResourceTags(1001L, request, new UserContext(401L, "TEACHER"));

        ArgumentCaptor<KnowledgePointEntity> knowledgePointCaptor = ArgumentCaptor.forClass(KnowledgePointEntity.class);
        verify(knowledgePointRepository).save(knowledgePointCaptor.capture());
        assertThat(knowledgePointCaptor.getValue()).satisfies(created -> {
            assertThat(created.getParentId()).isEqualTo(2L);
            assertThat(created.getName()).isEqualTo("工具");
            assertThat(created.getNodeType()).isEqualTo(KnowledgePointType.POINT);
            assertThat(created.getActive()).isTrue();
        });

        ArgumentCaptor<Iterable<ResourceKnowledgePointEntity>> relationCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(resourceKnowledgePointRepository).saveAll(relationCaptor.capture());
        assertThat(toList(relationCaptor.getValue()))
                .extracting(ResourceKnowledgePointEntity::getKnowledgePointId)
                .containsExactly(2L, 8L);

        ArgumentCaptor<Iterable<ResourceTagEntity>> tagCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(resourceTagRepository).saveAll(tagCaptor.capture());
        assertThat(toList(tagCaptor.getValue()))
                .extracting(ResourceTagEntity::getLabel)
                .containsExactly("工具", "Python");
    }

    @Test
    void generateExerciseQuestionsDelegatesToAiGenerationService() {
        ExerciseGenerateRequest request = new ExerciseGenerateRequest();
        request.setTitle("Python 条件语句");
        request.setTagLabels(List.of("条件分支"));
        request.setQuestionCount(3);
        ResourceResponse.ExerciseQuestionResponse generated = ResourceResponse.ExerciseQuestionResponse.builder()
                .id("q1")
                .stem("if 语句的主要作用是什么？")
                .options(List.of(
                        ResourceResponse.ExerciseOptionResponse.builder().id("A").text("根据条件选择执行路径").build(),
                        ResourceResponse.ExerciseOptionResponse.builder().id("B").text("定义模块").build()
                ))
                .answer("A")
                .explanation("if 用于条件判断。")
                .build();
        ExerciseGenerateResponse generatedResponse = ExerciseGenerateResponse.builder()
                .title("Python 条件语句练习")
                .description("围绕条件分支生成的单选题")
                .questions(List.of(generated))
                .build();
        when(exerciseQuestionGenerationService.generate(request)).thenReturn(generatedResponse);

        ExerciseGenerateResponse response = courseFacadeService.generateExerciseQuestions(
                request,
                new UserContext(401L, "TEACHER")
        );

        assertThat(response).isSameAs(generatedResponse);
        assertThat(response.getQuestions()).containsExactly(generated);
        verify(exerciseQuestionGenerationService).generate(request);
    }

    @Test
    void generateExerciseQuestionsUsesCourseAndChapterContextWhenRequestIsBlank() {
        CourseEntity course = CourseEntity.builder()
                .id(301L)
                .teacherId(401L)
                .title("Python 入门")
                .description("面向初学者的 Python 基础课程")
                .status(CourseStatus.DRAFT)
                .visibilityType(CourseVisibilityType.PUBLIC)
                .build();
        ChapterEntity chapter = ChapterEntity.builder()
                .id(201L)
                .courseId(301L)
                .title("条件与循环")
                .description("控制流程基础")
                .orderIndex(1)
                .build();
        ResourceEntity resource = ResourceEntity.builder()
                .id(1001L)
                .chapterId(201L)
                .title("if 语句示例")
                .description("通过条件判断选择不同执行路径")
                .type(ResourceType.VIDEO)
                .orderIndex(1)
                .build();
        ExerciseGenerateRequest request = new ExerciseGenerateRequest();
        request.setCourseId(301L);
        request.setChapterId(201L);
        request.setQuestionCount(5);
        ResourceResponse.ExerciseQuestionResponse generated = ResourceResponse.ExerciseQuestionResponse.builder()
                .id("q1")
                .stem("根据上下文生成的问题")
                .options(List.of(
                        ResourceResponse.ExerciseOptionResponse.builder().id("A").text("正确项").build(),
                        ResourceResponse.ExerciseOptionResponse.builder().id("B").text("干扰项").build()
                ))
                .answer("A")
                .build();

        when(chapterRepository.findById(201L)).thenReturn(Optional.of(chapter));
        when(courseRepository.findById(301L)).thenReturn(Optional.of(course));
        when(resourceRepository.findByChapterIdOrderByOrderIndexAscIdAsc(201L)).thenReturn(List.of(resource));
        when(chapterRepository.findByCourseIdOrderByOrderIndexAscIdAsc(301L)).thenReturn(List.of(chapter));
        when(resourceRepository.findByChapterIdInOrderByOrderIndexAscIdAsc(List.of(201L))).thenReturn(List.of(resource));
        ExerciseGenerateResponse generatedResponse = ExerciseGenerateResponse.builder()
                .title("Python 条件与循环练习")
                .description("结合课程和单元上下文生成的习题")
                .questions(List.of(generated))
                .build();
        when(exerciseQuestionGenerationService.generate(any(ExerciseGenerateRequest.class))).thenReturn(generatedResponse);

        ExerciseGenerateResponse response = courseFacadeService.generateExerciseQuestions(
                request,
                new UserContext(401L, "TEACHER")
        );

        ArgumentCaptor<ExerciseGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ExerciseGenerateRequest.class);
        verify(exerciseQuestionGenerationService).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTitle()).isEqualTo("Python 入门 - 条件与循环");
        assertThat(requestCaptor.getValue().getDescription())
                .contains("Python 入门")
                .contains("条件与循环")
                .contains("if 语句示例");
        assertThat(response.getTitle()).isEqualTo("Python 条件与循环练习");
        assertThat(response.getDescription()).isEqualTo("结合课程和单元上下文生成的习题");
        assertThat(response.getQuestions()).containsExactly(generated);
    }

    @Test
    void createResourceDefersTagAgentRequestUntilAfterCommit() {
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
        ResourceUpsertRequest request = new ResourceUpsertRequest();
        request.setTitle("函数极限讲义");
        request.setType(ResourceType.DOCUMENT.name());
        request.setUrl("resources/1001.pdf");
        request.setDescription("包含函数极限的定义和例题");
        request.setOrderIndex(1);

        when(chapterRepository.findById(201L)).thenReturn(Optional.of(chapter));
        when(courseRepository.findById(301L)).thenReturn(Optional.of(course));
        when(resourceRepository.save(any(ResourceEntity.class))).thenAnswer(invocation -> {
            ResourceEntity resource = invocation.getArgument(0);
            resource.setId(1001L);
            resource.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            resource.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            if (resource.getTaggingStatus() == null) {
                resource.setTaggingStatus(ResourceTaggingStatus.UNTAGGED);
            }
            return resource;
        });
        when(resourceStorageService.isManagedStorageKey("resources/1001.pdf")).thenReturn(false);
        when(resourceKnowledgePointRepository.findByResourceIdIn(anyCollection())).thenReturn(List.of());
        when(resourceTagRepository.findByResourceIdIn(anyCollection())).thenReturn(List.of());
        when(resourceTagAgentClient.requestTagging(any())).thenReturn(Optional.empty());

        TransactionSynchronizationManager.initSynchronization();
        try {
            courseFacadeService.createResource(201L, request, new UserContext(401L, "TEACHER"));

            verify(resourceTagAgentClient, never()).requestTagging(any());
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.getFirst().afterCommit();

            verify(resourceTagAgentClient).requestTagging(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
