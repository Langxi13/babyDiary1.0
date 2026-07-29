package com.langxi.babydiary.v3.space.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollaborationRepository {
    List<Member> findMembers(long spaceId);

    long insertInvitation(NewInvitation invitation);

    Optional<Invitation> findInvitation(byte[] tokenHash, LocalDateTime now);

    boolean acceptInvitation(long invitationId, long accountId, LocalDateTime now);

    void insertMember(long spaceId, long accountId, String role);

    Optional<Membership> findMembership(long spaceId, UUID accountPublicId);

    int countOwners(long spaceId);

    boolean updateRole(long spaceId, long accountId, String role);

    boolean removeMember(long spaceId, long accountId);

    record Member(UUID id, String username, String email, String role, String status, LocalDateTime joinedAt) {
        @com.fasterxml.jackson.annotation.JsonProperty("userId")
        public UUID userId() { return id; }
    }

    record Membership(long accountId, String role, String status) {
    }

    record NewInvitation(UUID publicId, long spaceId, long invitedBy, String email, byte[] tokenHash,
                         String role, LocalDateTime expiresAt) {
    }

    record Invitation(long internalId, long spaceId, String email, String role, LocalDateTime expiresAt) {
    }
}
