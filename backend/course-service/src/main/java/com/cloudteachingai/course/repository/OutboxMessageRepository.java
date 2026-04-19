package com.cloudteachingai.course.repository;

import com.cloudteachingai.course.entity.OutboxMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, Long> {

    @Query(value = """
            SELECT *
            FROM outbox_message
            WHERE sent = false
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessageEntity> lockNextBatch(@Param("limit") int limit);
}
