package com.langxi.babydiary.platform.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface OutboxMapper {
    @Insert("""
            INSERT INTO sync_change(space_id,entity_type,entity_public_id,operation,revision,actor_id,created_at)
            VALUES(#{spaceId},#{aggregateType},#{aggregateId},'UPSERT',#{revision},#{actorId},#{createdAt})
            """)
    void insertSync(long spaceId, String aggregateType, byte[] aggregateId, int revision,
                    long actorId, LocalDateTime createdAt);

    @Insert("""
            INSERT INTO outbox_event(public_id,space_id,aggregate_type,aggregate_public_id,event_type,payload,created_at)
            VALUES(UUID_TO_BIN(UUID()),#{spaceId},#{aggregateType},#{aggregateId},#{eventType},#{payload},#{createdAt})
            """
    )
    void insertOutbox(long spaceId, String aggregateType, byte[] aggregateId, String eventType,
                      String payload, LocalDateTime createdAt);
}
