package com.langxi.babydiary.notification.infrastructure;

import com.langxi.babydiary.notification.application.NotificationRepository;
import com.langxi.babydiary.platform.application.BinaryUuid;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisNotificationRepository implements NotificationRepository {
    private final NotificationMapper mapper;

    public MyBatisNotificationRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Row> findPage(long accountId, int limit, long offset) {
        return mapper.findPage(accountId, limit, offset).stream().map(row -> new Row(
                BinaryUuid.fromBytes(row.publicId()), row.spacePublicId() == null ? null : BinaryUuid.fromBytes(row.spacePublicId()),
                row.type(), row.title(), row.body(), row.targetRef(), row.readAt(), row.createdAt())).toList();
    }

    @Override public long count(long accountId) { return mapper.count(accountId); }
    @Override public long countUnread(long accountId) { return mapper.countUnread(accountId); }
    @Override public void markRead(long accountId, UUID publicId, LocalDateTime now) { mapper.markRead(accountId, BinaryUuid.toBytes(publicId), now); }
    @Override public void markAllRead(long accountId, LocalDateTime now) { mapper.markAllRead(accountId, now); }
}
