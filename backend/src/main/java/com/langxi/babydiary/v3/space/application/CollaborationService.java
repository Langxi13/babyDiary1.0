package com.langxi.babydiary.v3.space.application;

import com.langxi.babydiary.v3.platform.application.V3Exception;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class CollaborationService {
    private final SpaceAccess spaces;
    private final CollaborationRepository collaboration;
    private final SecureRandom random = new SecureRandom();

    public CollaborationService(SpaceAccess spaces, CollaborationRepository collaboration) {
        this.spaces = spaces;
        this.collaboration = collaboration;
    }

    public List<CollaborationRepository.Member> members(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return collaboration.findMembers(space.internalId());
    }

    @Transactional
    public InvitationCreated invite(UUID spaceId, long accountId, String email, String role) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!("OWNER".equals(space.role()) || "ADMIN".equals(space.role()))) {
            throw V3Exception.forbidden("INVITATION_FORBIDDEN", "只有空间管理员可以发出邀请");
        }
        String normalizedEmail = email == null ? "" : email.trim();
        if (!normalizedEmail.contains("@") || normalizedEmail.length() > 255) {
            throw V3Exception.badRequest("INVITATION_EMAIL_INVALID", "邀请邮箱格式无效");
        }
        String normalizedRole = "VIEWER".equals(role) ? "VIEWER" : "MEMBER";
        byte[] token = new byte[32];
        random.nextBytes(token);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        LocalDateTime expires = LocalDateTime.now(ZoneOffset.UTC).plusDays(7);
        UUID publicId = UUID.randomUUID();
        collaboration.insertInvitation(new CollaborationRepository.NewInvitation(publicId, space.internalId(),
                accountId, normalizedEmail, sha256(rawToken), normalizedRole, expires));
        return new InvitationCreated(publicId, rawToken, expires, normalizedRole);
    }

    @Transactional
    public void accept(String rawToken, long accountId) {
        if (rawToken == null || rawToken.isBlank()) throw V3Exception.badRequest("INVITATION_TOKEN_INVALID", "邀请链接无效");
        CollaborationRepository.Invitation invitation = collaboration.findInvitation(sha256(rawToken), LocalDateTime.now(ZoneOffset.UTC))
                .orElseThrow(() -> V3Exception.notFound("INVITATION_NOT_FOUND", "邀请不存在、已过期或已使用"));
        if (!collaboration.acceptInvitation(invitation.internalId(), accountId, LocalDateTime.now(ZoneOffset.UTC))) {
            throw V3Exception.conflict("INVITATION_ALREADY_USED", "邀请已被使用");
        }
        try {
            collaboration.insertMember(invitation.spaceId(), accountId, invitation.role());
        } catch (DuplicateKeyException ignored) {
        }
    }

    @Transactional
    public void updateRole(UUID spaceId, long actorId, UUID targetAccountId, String role) {
        SpaceAccess.SpaceContext space = requireSharedOwner(spaceId, actorId);
        CollaborationRepository.Membership target = membership(space.internalId(), targetAccountId);
        String normalized = switch (role == null ? "" : role.toUpperCase(java.util.Locale.ROOT)) {
            case "OWNER" -> "OWNER";
            case "ADMIN" -> "ADMIN";
            case "VIEWER" -> "VIEWER";
            default -> "MEMBER";
        };
        if ("OWNER".equals(target.role()) && !"OWNER".equals(normalized)
                && collaboration.countOwners(space.internalId()) <= 1) {
            throw V3Exception.conflict("SPACE_LAST_OWNER", "空间必须保留至少一名所有者");
        }
        if (!collaboration.updateRole(space.internalId(), target.accountId(), normalized)) {
            throw V3Exception.notFound("SPACE_MEMBER_NOT_FOUND", "成员不存在");
        }
    }

    @Transactional
    public void removeMember(UUID spaceId, long actorId, UUID targetAccountId) {
        SpaceAccess.SpaceContext space = requireSharedOwner(spaceId, actorId);
        CollaborationRepository.Membership target = membership(space.internalId(), targetAccountId);
        if ("OWNER".equals(target.role()) && collaboration.countOwners(space.internalId()) <= 1) {
            throw V3Exception.conflict("SPACE_LAST_OWNER", "不能移除空间的最后一名所有者");
        }
        if (!collaboration.removeMember(space.internalId(), target.accountId())) {
            throw V3Exception.notFound("SPACE_MEMBER_NOT_FOUND", "成员不存在");
        }
    }

    private SpaceAccess.SpaceContext requireSharedOwner(UUID spaceId, long actorId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, actorId);
        if (!"SHARED".equals(space.type())) {
            throw V3Exception.badRequest("PERSONAL_SPACE_MEMBERS_IMMUTABLE", "个人空间不支持成员管理");
        }
        if (!"OWNER".equals(space.role())) {
            throw V3Exception.forbidden("SPACE_OWNER_REQUIRED", "只有空间所有者可以管理成员");
        }
        return space;
    }

    private CollaborationRepository.Membership membership(long spaceId, UUID targetAccountId) {
        return collaboration.findMembership(spaceId, targetAccountId)
                .filter(item -> "ACTIVE".equals(item.status()))
                .orElseThrow(() -> V3Exception.notFound("SPACE_MEMBER_NOT_FOUND", "成员不存在"));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record InvitationCreated(UUID id, String token, LocalDateTime expiresAt, String role) {
    }
}
