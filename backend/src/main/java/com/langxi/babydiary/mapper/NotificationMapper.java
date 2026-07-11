package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.AppNotification;
import com.langxi.babydiary.entity.PushSubscription;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationMapper {
    @Insert("INSERT IGNORE INTO notification(public_id,user_id,space_id,type,title,body,target_path,dedupe_key) " +
            "VALUES(#{publicId},#{userId},#{spaceId},#{type},#{title},#{body},#{targetPath},#{dedupeKey})")
    @Options(useGeneratedKeys = true, keyProperty = "notificationId")
    int insertNotification(AppNotification notification);

    @Select("SELECT notification_id notificationId,public_id publicId,user_id userId,space_id spaceId,type,title,body," +
            "target_path targetPath,dedupe_key dedupeKey,read_at readAt,created_at createdAt " +
            "FROM notification WHERE user_id=#{userId} ORDER BY created_at DESC,notification_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<AppNotification> findPage(@Param("userId") Integer userId, @Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id=#{userId}")
    int count(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id=#{userId} AND read_at IS NULL")
    int countUnread(@Param("userId") Integer userId);

    @Update("UPDATE notification SET read_at=COALESCE(read_at,NOW()) WHERE user_id=#{userId} AND public_id=#{publicId}")
    int markRead(@Param("userId") Integer userId, @Param("publicId") String publicId);

    @Update("UPDATE notification SET read_at=COALESCE(read_at,NOW()) WHERE user_id=#{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") Integer userId);

    @Insert("INSERT INTO push_subscription(user_id,endpoint_hash,endpoint,p256dh,auth_secret,user_agent) " +
            "VALUES(#{userId},#{endpointHash},#{endpoint},#{p256dh},#{authSecret},#{userAgent}) " +
            "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),endpoint=VALUES(endpoint),p256dh=VALUES(p256dh)," +
            "auth_secret=VALUES(auth_secret),user_agent=VALUES(user_agent),revoked_at=NULL")
    void upsertSubscription(PushSubscription subscription);

    @Select("SELECT subscription_id subscriptionId,user_id userId,endpoint_hash endpointHash,endpoint,p256dh," +
            "auth_secret authSecret,user_agent userAgent,created_at createdAt,last_success_at lastSuccessAt,revoked_at revokedAt " +
            "FROM push_subscription WHERE user_id=#{userId} AND revoked_at IS NULL")
    List<PushSubscription> findActiveSubscriptions(@Param("userId") Integer userId);

    @Update("UPDATE push_subscription SET last_success_at=NOW() WHERE subscription_id=#{subscriptionId}")
    int markPushSuccess(@Param("subscriptionId") Long subscriptionId);

    @Update("UPDATE push_subscription SET revoked_at=NOW() WHERE subscription_id=#{subscriptionId}")
    int revokeSubscriptionById(@Param("subscriptionId") Long subscriptionId);

    @Update("UPDATE push_subscription SET revoked_at=NOW() WHERE user_id=#{userId} AND endpoint_hash=#{endpointHash}")
    int revokeSubscription(@Param("userId") Integer userId, @Param("endpointHash") String endpointHash);
}
