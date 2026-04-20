package com.cloudteachingai.course.event;

import com.cloudteachingai.course.exception.BusinessException;
import com.cloudteachingai.course.service.CourseFacadeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceTaggedConsumer {

    private final CourseFacadeService courseFacadeService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EventTopics.RESOURCE_TAGGED)
    public void consumeResourceTagged(String payload) {
        try {
            ResourceTaggedEvent event = objectMapper.readValue(payload, ResourceTaggedEvent.class);
            courseFacadeService.applyAiTaggedResource(event);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to consume resource.tagged payload={}", payload, ex);
            throw new IllegalStateException("Failed to consume resource.tagged", ex);
        }
    }
}
