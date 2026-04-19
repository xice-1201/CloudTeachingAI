package com.cloudteachingai.course.service;

import com.cloudteachingai.course.entity.OutboxMessageEntity;
import com.cloudteachingai.course.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    public String enqueue(String topic, Object payload) {
        String eventId = UUID.randomUUID().toString();
        outboxMessageRepository.save(OutboxMessageEntity.builder()
                .topic(topic)
                .eventId(eventId)
                .payload(serialize(payload))
                .sent(false)
                .build());
        return eventId;
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
