package com.langxi.babydiary.platform.infrastructure;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMapper {
    @Insert(
            """
            INSERT INTO sync_change(space_id,entity_type,entity_public_id,operation,revision,visibility,owner_id,actor_id,created_at)
            VALUES(#{spaceId},#{aggregateType},#{aggregateId},#{operation},#{revision},#{visibility},#{ownerId},#{actorId},#{createdAt})
            """)
    void insertSync(
            long spaceId,
            String aggregateType,
            byte[] aggregateId,
            String operation,
            int revision,
            String visibility,
            Long ownerId,
            long actorId,
            LocalDateTime createdAt);

    @Insert(
            """
            INSERT INTO outbox_event(public_id,space_id,actor_id,aggregate_type,aggregate_public_id,event_type,payload,created_at)
            VALUES(UUID_TO_BIN(UUID()),#{spaceId},#{actorId},#{aggregateType},#{aggregateId},#{eventType},#{payload},#{createdAt})
            """)
    void insertOutbox(
            long spaceId,
            long actorId,
            String aggregateType,
            byte[] aggregateId,
            String eventType,
            String payload,
            LocalDateTime createdAt);
}
