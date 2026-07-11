package com.langxi.babydiary.mapper;

import com.langxi.babydiary.dto.SyncChangeVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SyncMapper {
    @Insert("INSERT INTO sync_change(space_id,entity_type,entity_public_id,operation,revision,actor_user_id,payload_json) " +
            "VALUES(#{spaceId},#{entityType},#{entityId},#{operation},#{revision},#{actorUserId},#{payloadJson})")
    void insertChange(@Param("spaceId") Long spaceId,
                      @Param("entityType") String entityType,
                      @Param("entityId") String entityId,
                      @Param("operation") String operation,
                      @Param("revision") Integer revision,
                      @Param("actorUserId") Integer actorUserId,
                      @Param("payloadJson") String payloadJson);

    @Select("SELECT c.change_seq AS `cursor`,c.entity_type entityType,c.entity_public_id entityId,c.operation," +
            "c.revision,c.actor_user_id actorUserId," +
            "CASE WHEN c.entity_type='DIARY' AND d.locked=1 THEN JSON_OBJECT('publicId',d.public_id,'locked',true,'version',d.version,'deleted',d.deleted_at IS NOT NULL) ELSE c.payload_json END payloadJson," +
            "c.created_at createdAt FROM sync_change c " +
            "LEFT JOIN diary d ON c.entity_type='DIARY' AND d.public_id COLLATE utf8mb4_unicode_ci=c.entity_public_id " +
            "WHERE c.space_id=#{spaceId} AND c.change_seq>#{cursor} " +
            "AND (c.entity_type<>'DIARY' OR d.diary_id IS NULL OR d.visibility='SHARED' OR d.user_id=#{userId}) " +
            "ORDER BY c.change_seq ASC LIMIT #{limit}")
    List<SyncChangeVO> findChanges(@Param("spaceId") Long spaceId,
                                   @Param("userId") Integer userId,
                                   @Param("cursor") Long cursor,
                                   @Param("limit") int limit);

    @Select("SELECT result_json FROM sync_operation WHERE operation_id=#{operationId} AND user_id=#{userId} AND space_id=#{spaceId}")
    String findOperationResult(@Param("operationId") String operationId,
                               @Param("userId") Integer userId,
                               @Param("spaceId") Long spaceId);

    @Insert("INSERT INTO sync_operation(operation_id,user_id,space_id,result_json) VALUES(#{operationId},#{userId},#{spaceId},#{resultJson})")
    void insertOperationResult(@Param("operationId") String operationId,
                               @Param("userId") Integer userId,
                               @Param("spaceId") Long spaceId,
                               @Param("resultJson") String resultJson);
}
