package com.langxi.babydiary.space.infrastructure;

import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.space.application.CollaborationRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisCollaborationRepository implements CollaborationRepository {
    private final CollaborationMapper mapper;

    public MyBatisCollaborationRepository(CollaborationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Member> findMembers(long spaceId) {
        return mapper.findMembers(spaceId).stream().map(row -> new Member(BinaryUuid.fromBytes(row.publicId()),
                row.username(), row.email(), row.role(), row.status(), row.joinedAt())).toList();
    }

    @Override
    public long insertInvitation(NewInvitation invitation) {
        CollaborationMapper.InvitationInsert row = new CollaborationMapper.InvitationInsert(
                BinaryUuid.toBytes(invitation.publicId()), invitation.spaceId(), invitation.invitedBy(), invitation.email(),
                invitation.tokenHash(), invitation.role(), invitation.expiresAt());
        mapper.insertInvitation(row);
        return row.getInvitationId() == null ? 0 : row.getInvitationId();
    }

    @Override
    public Optional<Invitation> findInvitation(byte[] tokenHash, LocalDateTime now) {
        return Optional.ofNullable(mapper.findInvitation(tokenHash, now)).map(row ->
                new Invitation(row.invitationId(), row.spaceId(), row.email(), row.role(), row.expiresAt()));
    }

    @Override
    public boolean acceptInvitation(long invitationId, long accountId, LocalDateTime now) {
        return mapper.acceptInvitation(invitationId, accountId, now) == 1;
    }

    @Override
    public void insertMember(long spaceId, long accountId, String role) {
        mapper.insertMember(spaceId, accountId, role);
    }

    @Override
    public Optional<Membership> findMembership(long spaceId, UUID accountPublicId) {
        return Optional.ofNullable(mapper.findMembership(spaceId, BinaryUuid.toBytes(accountPublicId)))
                .map(row -> new Membership(row.accountId(), row.role(), row.status()));
    }

    @Override
    public int countOwners(long spaceId) { return mapper.countOwners(spaceId); }

    @Override
    public boolean updateRole(long spaceId, long accountId, String role) {
        return mapper.updateRole(spaceId, accountId, role) == 1;
    }

    @Override
    public boolean removeMember(long spaceId, long accountId) {
        return mapper.removeMember(spaceId, accountId) == 1;
    }
}
