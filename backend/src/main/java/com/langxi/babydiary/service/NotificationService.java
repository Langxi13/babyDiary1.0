package com.langxi.babydiary.service;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.dto.NotificationVO;
import com.langxi.babydiary.dto.PushSubscriptionDTO;
import com.langxi.babydiary.entity.AppNotification;
import com.langxi.babydiary.entity.PushSubscription;
import com.langxi.babydiary.mapper.NotificationMapper;
import com.langxi.babydiary.mapper.SpaceMapper;
import com.langxi.babydiary.util.SecureTokens;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final SpaceMapper spaceMapper;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationMapper notificationMapper,
                               SpaceMapper spaceMapper,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationMapper = notificationMapper;
        this.spaceMapper = spaceMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void notifySpaceMembers(Long spaceId, Integer actorUserId, String type, String title,
                                   String body, String targetPath, String eventKey) {
        for (Integer recipientId : spaceMapper.listActiveMemberIds(spaceId)) {
            if (recipientId.equals(actorUserId)) continue;
            AppNotification notification = new AppNotification();
            notification.setPublicId(UUID.randomUUID().toString());
            notification.setUserId(recipientId);
            notification.setSpaceId(spaceId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setBody(body);
            notification.setTargetPath(targetPath);
            notification.setDedupeKey(eventKey == null ? null : type + ":" + eventKey);
            if (notificationMapper.insertNotification(notification) == 1) {
                eventPublisher.publishEvent(new PushNotificationEvent(recipientId, title, body, targetPath));
            }
        }
    }

    @Transactional
    public void notifyUser(Integer userId, Long spaceId, String type, String title,
                           String body, String targetPath, String dedupeKey) {
        AppNotification notification = new AppNotification();
        notification.setPublicId(UUID.randomUUID().toString());
        notification.setUserId(userId);
        notification.setSpaceId(spaceId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setTargetPath(targetPath);
        notification.setDedupeKey(dedupeKey);
        if (notificationMapper.insertNotification(notification) == 1) {
            eventPublisher.publishEvent(new PushNotificationEvent(userId, title, body, targetPath));
        }
    }

    public PageResult<NotificationVO> list(Integer userId, int page, int size) {
        int normalizedPage = Pagination.normalizePage(page);
        int normalizedSize = Pagination.normalizeSize(size);
        return new PageResult<>(notificationMapper.findPage(userId, normalizedSize,
                        Pagination.offset(normalizedPage, normalizedSize)).stream().map(NotificationVO::from).toList(),
                normalizedPage, normalizedSize, (long) notificationMapper.count(userId));
    }

    public int unreadCount(Integer userId) {
        return notificationMapper.countUnread(userId);
    }

    public void markRead(Integer userId, String publicId) {
        notificationMapper.markRead(userId, publicId);
    }

    public void markAllRead(Integer userId) {
        notificationMapper.markAllRead(userId);
    }

    public void subscribe(Integer userId, PushSubscriptionDTO dto, String userAgent) {
        URI endpoint = URI.create(dto.getEndpoint());
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || (endpoint.getPort() != -1 && endpoint.getPort() != 443)) {
            throw new IllegalArgumentException("推送地址必须使用HTTPS");
        }
        validatePublicHost(endpoint.getHost());
        PushSubscription subscription = new PushSubscription();
        subscription.setUserId(userId);
        subscription.setEndpointHash(SecureTokens.sha256(dto.getEndpoint()));
        subscription.setEndpoint(dto.getEndpoint());
        subscription.setP256dh(dto.getP256dh());
        subscription.setAuthSecret(dto.getAuth());
        subscription.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(500, userAgent.length())));
        notificationMapper.upsertSubscription(subscription);
    }

    public void unsubscribe(Integer userId, String endpoint) {
        notificationMapper.revokeSubscription(userId, SecureTokens.sha256(endpoint));
    }

    private void validatePublicHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || isReservedAddress(address)) {
                    throw new IllegalArgumentException("推送地址不能指向内网");
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("推送地址无法解析");
        }
    }

    private boolean isReservedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) return (bytes[0] & 0xfe) == 0xfc;
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return (first == 100 && second >= 64 && second <= 127)
                || (first == 198 && (second == 18 || second == 19));
    }
}
