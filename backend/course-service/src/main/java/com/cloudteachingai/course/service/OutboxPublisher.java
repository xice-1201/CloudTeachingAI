package com.cloudteachingai.course.service;

import com.cloudteachingai.course.entity.OutboxMessageEntity;
import com.cloudteachingai.course.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingMessages() {
        List<OutboxMessageEntity> messages = outboxMessageRepository.lockNextBatch(batchSize);
        for (OutboxMessageEntity message : messages) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getEventId(), message.getPayload()).get();
                message.setSent(true);
            } catch (Exception ex) {
                log.warn("Failed to publish outbox message: topic={}, eventId={}",
                        message.getTopic(),
                        message.getEventId(),
                        ex);
                break;
            }
        }
    }
}
