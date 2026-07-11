package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.SpaceInvitation;
import com.langxi.babydiary.entity.SpaceMember;
import org.apache.ibatis.annotations.*;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface SpaceMapper {
    @Insert("INSERT INTO diary_space(public_id,name,type,created_by,personal_owner_id) VALUES(#{publicId},#{name},#{type},#{createdBy},#{personalOwnerId})")
    @Options(useGeneratedKeys = true, keyProperty = "spaceId")
    void insertSpace(DiarySpace space);

    @Insert("INSERT INTO space_member(space_id,user_id,role,status) VALUES(#{spaceId},#{userId},#{role},'ACTIVE')")
    void insertMember(@Param("spaceId") Long spaceId, @Param("userId") Integer userId, @Param("role") String role);

    @Insert("INSERT IGNORE INTO space_storage_usage(space_id,used_bytes) VALUES(#{spaceId},0)")
    int ensureStorageUsage(@Param("spaceId") Long spaceId);

    @Select("SELECT space_id spaceId, public_id publicId, name, type, created_by createdBy, personal_owner_id personalOwnerId, created_at createdAt, updated_at updatedAt FROM diary_space WHERE personal_owner_id=#{userId}")
    DiarySpace findPersonalSpace(@Param("userId") Integer userId);

    @Select("SELECT s.space_id spaceId,s.public_id publicId,s.name,s.type,s.created_by createdBy," +
            "s.personal_owner_id personalOwnerId,s.created_at createdAt,s.updated_at updatedAt,m.role memberRole," +
            "(SELECT COUNT(*) FROM space_member active_member WHERE active_member.space_id=s.space_id AND active_member.status='ACTIVE') memberCount " +
            "FROM diary_space s JOIN space_member m ON m.space_id=s.space_id " +
            "WHERE m.user_id=#{userId} AND m.status='ACTIVE' ORDER BY s.type='PERSONAL' DESC,s.created_at")
    List<DiarySpace> listSpaces(@Param("userId") Integer userId);

    @Update("UPDATE diary_space SET name=#{name} WHERE space_id=#{spaceId}")
    int updateSpaceName(@Param("spaceId") Long spaceId, @Param("name") String name);

    @Select("SELECT space_id spaceId,public_id publicId,name,type,created_by createdBy,personal_owner_id personalOwnerId,created_at createdAt,updated_at updatedAt FROM diary_space WHERE public_id=#{publicId}")
    DiarySpace findByPublicId(@Param("publicId") String publicId);

    @Select("SELECT space_id spaceId,public_id publicId,name,type,created_by createdBy,personal_owner_id personalOwnerId,created_at createdAt,updated_at updatedAt FROM diary_space WHERE space_id=#{spaceId}")
    DiarySpace findById(@Param("spaceId") Long spaceId);

    @Select("SELECT space_id FROM diary_space WHERE space_id=#{spaceId} FOR UPDATE")
    Long lockSpace(@Param("spaceId") Long spaceId);

    @Select("SELECT m.space_id spaceId,m.user_id userId,m.role,m.status,m.joined_at joinedAt,u.username,u.avatar_path avatarPath FROM space_member m JOIN user u ON u.user_id=m.user_id WHERE m.space_id=#{spaceId} AND m.user_id=#{userId} AND m.status='ACTIVE'")
    SpaceMember findMember(@Param("spaceId") Long spaceId, @Param("userId") Integer userId);

    @Select("SELECT m.space_id spaceId,m.user_id userId,m.role,m.status,m.joined_at joinedAt,u.username,u.avatar_path avatarPath FROM space_member m JOIN user u ON u.user_id=m.user_id WHERE m.space_id=#{spaceId} AND m.status='ACTIVE' ORDER BY m.role='OWNER' DESC,m.joined_at")
    List<SpaceMember> listMembers(@Param("spaceId") Long spaceId);

    @Select("SELECT COUNT(*) FROM space_member WHERE space_id=#{spaceId} AND status='ACTIVE'")
    int countMembers(@Param("spaceId") Long spaceId);

    @Select("SELECT user_id FROM space_member WHERE space_id=#{spaceId} AND status='ACTIVE'")
    List<Integer> listActiveMemberIds(@Param("spaceId") Long spaceId);

    @Select("SELECT COUNT(*) FROM space_member WHERE space_id=#{spaceId} AND role='OWNER' AND status='ACTIVE'")
    int countOwners(@Param("spaceId") Long spaceId);

    @Update("UPDATE space_member SET role=#{role} WHERE space_id=#{spaceId} AND user_id=#{userId} AND status='ACTIVE'")
    int updateMemberRole(@Param("spaceId") Long spaceId, @Param("userId") Integer userId, @Param("role") String role);

    @Delete("DELETE FROM space_member WHERE space_id=#{spaceId} AND user_id=#{userId}")
    int deleteMember(@Param("spaceId") Long spaceId, @Param("userId") Integer userId);

    @Insert("INSERT INTO space_invitation(space_id,invited_by,email,token_hash,role,status,expires_at) VALUES(#{spaceId},#{invitedBy},#{email},#{tokenHash},#{role},#{status},#{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "invitationId")
    void insertInvitation(SpaceInvitation invitation);

    @Select("SELECT invitation_id invitationId,space_id spaceId,invited_by invitedBy,email,token_hash tokenHash,role,status,expires_at expiresAt,accepted_by acceptedBy,created_at createdAt FROM space_invitation WHERE token_hash=#{tokenHash}")
    SpaceInvitation findInvitation(@Param("tokenHash") String tokenHash);

    @Update("UPDATE space_invitation SET status='ACCEPTED',accepted_by=#{userId} WHERE invitation_id=#{invitationId} AND status='PENDING'")
    int acceptInvitation(@Param("invitationId") Long invitationId, @Param("userId") Integer userId);

    @Insert("INSERT INTO space_member(space_id,user_id,role,status) VALUES(#{spaceId},#{userId},#{role},'ACTIVE') ON DUPLICATE KEY UPDATE role=VALUES(role),status='ACTIVE'")
    void upsertMember(@Param("spaceId") Long spaceId, @Param("userId") Integer userId, @Param("role") String role);
}
