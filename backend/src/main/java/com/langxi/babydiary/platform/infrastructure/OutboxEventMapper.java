package com.langxi.babydiary.platform.infrastructure;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxEventMapper {
    @Update(
            """
            UPDATE outbox_event
            SET claimed_at=#{now},claimed_by=#{claimToken},attempt_count=attempt_count+1,last_error=NULL
            WHERE processed_at IS NULL AND failed_at IS NULL AND claimed_at IS NULL
              AND available_at<=#{now} AND attempt_count<max_attempts
            ORDER BY available_at,event_id LIMIT 1
            """)
    int claim(@Param("claimToken") String claimToken, @Param("now") LocalDateTime now);

    @Select(
            """
            SELECT e.event_id,e.public_id,e.space_id,s.public_id AS space_public_id,e.actor_id,
                   e.aggregate_type,e.aggregate_public_id,e.event_type,e.payload,
                   e.attempt_count,e.max_attempts
            FROM outbox_event e LEFT JOIN diary_space s ON s.space_id=e.space_id
            WHERE e.processed_at IS NULL AND e.failed_at IS NULL AND e.claimed_by=#{claimToken}
            """)
    EventRow findClaimed(String claimToken);

    @Update(
            """
            UPDATE outbox_event SET processed_at=#{now},claimed_at=NULL,claimed_by=NULL,last_error=NULL
            WHERE event_id=#{eventId} AND processed_at IS NULL AND failed_at IS NULL
              AND claimed_by=#{claimToken}
            """)
    int succeed(
            @Param("eventId") long eventId,
            @Param("claimToken") String claimToken,
            @Param("now") LocalDateTime now);

    @Update(
            """
            <script>
            UPDATE outbox_event SET available_at=#{availableAt},claimed_at=NULL,claimed_by=NULL,
              last_error=#{error},failed_at=<choose><when test="terminal">#{now}</when><otherwise>NULL</otherwise></choose>
            WHERE event_id=#{eventId} AND processed_at IS NULL AND failed_at IS NULL
              AND claimed_by=#{claimToken}
            </script>
            """)
    int fail(
            @Param("eventId") long eventId,
            @Param("claimToken") String claimToken,
            @Param("terminal") boolean terminal,
            @Param("availableAt") LocalDateTime availableAt,
            @Param("error") String error,
            @Param("now") LocalDateTime now);

    @Update(
            """
            UPDATE outbox_event SET claimed_at=NULL,claimed_by=NULL,available_at=#{now},
              last_error='事件处理超时，已自动重新排队'
            WHERE processed_at IS NULL AND failed_at IS NULL AND claimed_at<#{staleBefore}
              AND attempt_count<max_attempts
            """)
    int recoverRetryable(
            @Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);

    @Update(
            """
            UPDATE outbox_event SET claimed_at=NULL,claimed_by=NULL,failed_at=#{now},
              last_error='事件处理超时且已达到最大重试次数'
            WHERE processed_at IS NULL AND failed_at IS NULL AND claimed_at<#{staleBefore}
              AND attempt_count>=max_attempts
            """)
    int failExhausted(
            @Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);

    record EventRow(
            long eventId,
            byte[] publicId,
            Long spaceId,
            byte[] spacePublicId,
            Long actorId,
            String aggregateType,
            byte[] aggregatePublicId,
            String eventType,
            String payload,
            int attemptCount,
            int maxAttempts) {}
}
