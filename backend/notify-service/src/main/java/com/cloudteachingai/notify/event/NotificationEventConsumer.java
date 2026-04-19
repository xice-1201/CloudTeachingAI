package com.cloudteachingai.notify.event;

import com.cloudteachingai.notify.dto.CreateNotificationRequest;
import com.cloudteachingai.notify.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EventTopics.NOTIFICATION_SEND)
    public void consumeNotificationSend(
            @Header(KafkaHeaders.RECEIVED_KEY) String eventId,
            String payload) {
        try {
            NotificationSendEvent event = objectMapper.readValue(payload, NotificationSendEvent.class);
            CreateNotificationRequest request = new CreateNotificationRequest();
            request.setUserId(event.getUserId());
            request.setType(event.getType());
            request.setTitle(event.getTitle());
            request.setContent(event.getContent());
            notificationService.createNotification(request, eventId);
        } catch (Exception ex) {
            log.error("Failed to consume notification.send event: eventId={}", eventId, ex);
            throw new IllegalStateException("Failed to consume notification.send event", ex);
        }
    }
}
