package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.SpaceInvitation;
import com.langxi.babydiary.entity.SpaceMember;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.SpaceMapper;
import com.langxi.babydiary.mapper.UserMapper;
import com.langxi.babydiary.util.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SpaceService {
    private final SpaceMapper spaceMapper;
    private final UserMapper userMapper;
    private final TagService tagService;

    public SpaceService(SpaceMapper spaceMapper, UserMapper userMapper, TagService tagService) {
        this.spaceMapper = spaceMapper;
        this.userMapper = userMapper;
        this.tagService = tagService;
    }

    @Transactional
    public DiarySpace ensurePersonalSpace(Integer userId, String username) {
        DiarySpace existing = spaceMapper.findPersonalSpace(userId);
        if (existing != null) return existing;
        DiarySpace space = new DiarySpace();
        space.setPublicId(UUID.randomUUID().toString());
        space.setName(username + "的个人空间");
        space.setType("PERSONAL");
        space.setCreatedBy(userId);
        space.setPersonalOwnerId(userId);
        spaceMapper.insertSpace(space);
        spaceMapper.insertMember(space.getSpaceId(), userId, "OWNER");
        ensureStorageUsage(space.getSpaceId());
        DiarySpace stored = spaceMapper.findById(space.getSpaceId());
        return stored == null ? space : stored;
    }

    public List<SpaceVO> listSpaces(Integer userId) {
        return spaceMapper.listSpaces(userId).stream()
                .map(space -> SpaceVO.from(space, space.getMemberRole(), space.getMemberCount()))
                .toList();
    }

    @Transactional
    public SpaceVO createSharedSpace(Integer userId, CreateSpaceDTO dto) {
        DiarySpace space = new DiarySpace();
        space.setPublicId(UUID.randomUUID().toString());
        space.setName(dto.getName().trim());
        space.setType("SHARED");
        space.setCreatedBy(userId);
        spaceMapper.insertSpace(space);
        spaceMapper.insertMember(space.getSpaceId(), userId, "OWNER");
        ensureStorageUsage(space.getSpaceId());
        DiarySpace stored = spaceMapper.findById(space.getSpaceId());
        return SpaceVO.from(stored == null ? space : stored, "OWNER", 1);
    }

    public void ensureStorageUsage(Long spaceId) {
        spaceMapper.ensureStorageUsage(spaceId);
    }

    public DiarySpace requireSpace(String publicId) {
        DiarySpace space = spaceMapper.findByPublicId(publicId);
        if (space == null) throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        return space;
    }

    public SpaceMember requireMember(String publicId, Integer userId) {
        return requireMember(requireSpace(publicId), userId);
    }

    public SpaceMember requireMember(DiarySpace space, Integer userId) {
        SpaceMember member = spaceMapper.findMember(space.getSpaceId(), userId);
        if (member == null) throw new BusinessException(ErrorCode.SPACE_ACCESS_DENIED);
        return member;
    }

    public SpaceMember requireOwner(String publicId, Integer userId) {
        SpaceMember member = requireMember(publicId, userId);
        if (!"OWNER".equals(member.getRole())) throw new BusinessException(ErrorCode.SPACE_OWNER_REQUIRED);
        return member;
    }

    public List<SpaceMemberVO> listMembers(String publicId, Integer userId) {
        DiarySpace space = requireSpace(publicId);
        requireMember(space, userId);
        return spaceMapper.listMembers(space.getSpaceId()).stream().map(SpaceMemberVO::from).toList();
    }

    @Transactional
    public SpaceVO rename(String publicId, Integer userId, CreateSpaceDTO dto) {
        DiarySpace space = requireSpace(publicId);
        requireOwner(publicId, userId);
        spaceMapper.updateSpaceName(space.getSpaceId(), dto.getName().trim());
        space.setName(dto.getName().trim());
        return SpaceVO.from(space, "OWNER", spaceMapper.countMembers(space.getSpaceId()));
    }

    public List<TagVO> listTags(String publicId, Integer userId) {
        DiarySpace space = requireSpace(publicId);
        requireMember(space, userId);
        return tagService.findTagsBySpaceId(space.getSpaceId()).stream().map(TagVO::fromEntity).toList();
    }

    @Transactional
    public TagVO createTag(String publicId, Integer userId, TagCreateDTO dto) {
        DiarySpace space = requireSpace(publicId);
        requireMember(space, userId);
        return TagVO.fromEntity(tagService.createTag(userId, space.getSpaceId(), dto.getName(), dto.getColor()));
    }

    @Transactional
    public SpaceInvitationVO invite(String publicId, Integer userId, SpaceInviteDTO dto) {
        DiarySpace space = requireSpace(publicId);
        requireOwner(publicId, userId);
        if ("PERSONAL".equals(space.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "个人空间不能邀请成员");
        }
        String rawToken = SecureTokens.randomToken(32);
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS));
        SpaceInvitation invitation = new SpaceInvitation();
        invitation.setSpaceId(space.getSpaceId());
        invitation.setInvitedBy(userId);
        invitation.setEmail(dto.getEmail() == null || dto.getEmail().isBlank() ? null : dto.getEmail().trim().toLowerCase());
        invitation.setTokenHash(SecureTokens.sha256(rawToken));
        invitation.setRole(dto.getRole() == null ? "MEMBER" : dto.getRole());
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(expiresAt);
        spaceMapper.insertInvitation(invitation);
        return new SpaceInvitationVO(rawToken, expiresAt);
    }

    @Transactional
    public SpaceVO acceptInvitation(String rawToken, Integer userId) {
        SpaceInvitation invitation = spaceMapper.findInvitation(SecureTokens.sha256(rawToken));
        if (invitation == null || !"PENDING".equals(invitation.getStatus()) || invitation.getExpiresAt().before(Timestamp.from(Instant.now()))) {
            throw new BusinessException(ErrorCode.SPACE_INVITATION_INVALID);
        }
        if (invitation.getEmail() != null) {
            com.langxi.babydiary.entity.User user = userMapper.findById(userId);
            if (user == null || !Boolean.TRUE.equals(user.getEmailVerified())
                    || !invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
                throw new BusinessException(ErrorCode.SPACE_INVITATION_INVALID, "该邀请仅限指定的已验证邮箱使用");
            }
        }
        spaceMapper.upsertMember(invitation.getSpaceId(), userId, invitation.getRole());
        if (spaceMapper.acceptInvitation(invitation.getInvitationId(), userId) != 1) {
            throw new BusinessException(ErrorCode.SPACE_INVITATION_INVALID);
        }
        DiarySpace space = spaceMapper.listSpaces(userId).stream()
                .filter(item -> item.getSpaceId().equals(invitation.getSpaceId()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.SPACE_NOT_FOUND));
        return SpaceVO.from(space, invitation.getRole(), spaceMapper.countMembers(space.getSpaceId()));
    }

    @Transactional
    public void changeRole(String publicId, Integer actorUserId, Integer targetUserId, String role) {
        DiarySpace space = requireSpace(publicId);
        spaceMapper.lockSpace(space.getSpaceId());
        requireOwner(publicId, actorUserId);
        if (!"OWNER".equals(role) && !"MEMBER".equals(role)) throw new BusinessException(ErrorCode.BAD_REQUEST);
        SpaceMember target = requireMember(space, targetUserId);
        if ("OWNER".equals(target.getRole()) && !"OWNER".equals(role) && spaceMapper.countOwners(space.getSpaceId()) <= 1) {
            throw new BusinessException(ErrorCode.SPACE_LAST_OWNER);
        }
        spaceMapper.updateMemberRole(space.getSpaceId(), targetUserId, role);
    }

    @Transactional
    public void removeMember(String publicId, Integer actorUserId, Integer targetUserId) {
        DiarySpace space = requireSpace(publicId);
        spaceMapper.lockSpace(space.getSpaceId());
        SpaceMember actor = requireMember(space, actorUserId);
        SpaceMember target = requireMember(space, targetUserId);
        if (!actorUserId.equals(targetUserId) && !"OWNER".equals(actor.getRole())) {
            throw new BusinessException(ErrorCode.SPACE_OWNER_REQUIRED);
        }
        if ("OWNER".equals(target.getRole()) && spaceMapper.countOwners(space.getSpaceId()) <= 1) {
            throw new BusinessException(ErrorCode.SPACE_LAST_OWNER);
        }
        spaceMapper.deleteMember(space.getSpaceId(), targetUserId);
    }
}
