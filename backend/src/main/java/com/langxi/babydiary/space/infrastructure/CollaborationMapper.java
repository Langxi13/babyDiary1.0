package com.langxi.babydiary.space.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CollaborationMapper {
    @Select(
            """
            SELECT a.public_id,a.username,a.email,m.role,m.status,m.joined_at
            FROM space_member m JOIN account a ON a.account_id=m.account_id
            WHERE m.space_id=#{spaceId} AND m.status='ACTIVE' AND a.deleted_at IS NULL
            ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END,a.username
            """)
    List<MemberRow> findMembers(long spaceId);

    @Insert(
            """
            INSERT INTO space_invitation(public_id,space_id,invited_by,email,token_hash,role,status,expires_at,created_at)
            VALUES(#{publicId},#{spaceId},#{invitedBy},#{email},#{tokenHash},#{role},'PENDING',#{expiresAt},UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "invitationId")
    void insertInvitation(InvitationInsert row);

    @Select(
            """
            SELECT invitation_id,space_id,email,role,expires_at
            FROM space_invitation
            WHERE token_hash=#{tokenHash} AND status='PENDING' AND expires_at>#{now}
            """)
    InvitationRow findInvitation(
            @Param("tokenHash") byte[] tokenHash, @Param("now") LocalDateTime now);

    @Update(
            """
            UPDATE space_invitation SET status='ACCEPTED',accepted_by=#{accountId}
            WHERE invitation_id=#{invitationId} AND status='PENDING' AND expires_at>#{now}
            """)
    int acceptInvitation(
            @Param("invitationId") long invitationId,
            @Param("accountId") long accountId,
            @Param("now") LocalDateTime now);

    @Insert(
            "INSERT INTO space_member(space_id,account_id,role,status,joined_at) VALUES(#{spaceId},#{accountId},#{role},'ACTIVE',UTC_TIMESTAMP(6))")
    void insertMember(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("role") String role);

    @Select(
            "SELECT m.account_id,m.role,m.status FROM space_member m JOIN account a ON a.account_id=m.account_id "
                    + "WHERE m.space_id=#{spaceId} AND a.public_id=#{publicId}")
    MembershipRow findMembership(
            @Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    @Select(
            "SELECT COUNT(*) FROM space_member WHERE space_id=#{spaceId} AND role='OWNER' AND status='ACTIVE'")
    int countOwners(long spaceId);

    @Update(
            "UPDATE space_member SET role=#{role},status='ACTIVE' WHERE space_id=#{spaceId} AND account_id=#{accountId}")
    int updateRole(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("role") String role);

    @Update(
            "UPDATE space_member SET status='LEFT' WHERE space_id=#{spaceId} AND account_id=#{accountId} AND status='ACTIVE'")
    int removeMember(@Param("spaceId") long spaceId, @Param("accountId") long accountId);

    final class InvitationInsert {
        private Long invitationId;
        private final byte[] publicId;
        private final long spaceId;
        private final long invitedBy;
        private final String email;
        private final byte[] tokenHash;
        private final String role;
        private final LocalDateTime expiresAt;

        public InvitationInsert(
                byte[] publicId,
                long spaceId,
                long invitedBy,
                String email,
                byte[] tokenHash,
                String role,
                LocalDateTime expiresAt) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.invitedBy = invitedBy;
            this.email = email;
            this.tokenHash = tokenHash;
            this.role = role;
            this.expiresAt = expiresAt;
        }

        public Long getInvitationId() {
            return invitationId;
        }

        public void setInvitationId(Long invitationId) {
            this.invitationId = invitationId;
        }

        public byte[] getPublicId() {
            return publicId;
        }

        public long getSpaceId() {
            return spaceId;
        }

        public long getInvitedBy() {
            return invitedBy;
        }

        public String getEmail() {
            return email;
        }

        public byte[] getTokenHash() {
            return tokenHash;
        }

        public String getRole() {
            return role;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }
    }

    final class MemberRow {
        private byte[] publicId;
        private String username;
        private String email;
        private String role;
        private String status;
        private LocalDateTime joinedAt;

        public MemberRow() {}

        public byte[] publicId() {
            return publicId;
        }

        public String username() {
            return username;
        }

        public String email() {
            return email;
        }

        public String role() {
            return role;
        }

        public String status() {
            return status;
        }

        public LocalDateTime joinedAt() {
            return joinedAt;
        }

        public void setPublicId(byte[] publicId) {
            this.publicId = publicId;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setJoinedAt(LocalDateTime joinedAt) {
            this.joinedAt = joinedAt;
        }
    }

    record InvitationRow(
            long invitationId, long spaceId, String email, String role, LocalDateTime expiresAt) {}

    record MembershipRow(long accountId, String role, String status) {}
}
