package com.langxi.babydiary.notification.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PushSubscriptionMapper {
    @Insert(
            "INSERT INTO push_subscription(account_id,endpoint_hash,endpoint,p256dh,auth_secret,user_agent) VALUES(#{accountId},#{hash},#{endpoint},#{p256dh},#{auth},#{agent}) "
                    + "ON DUPLICATE KEY UPDATE account_id=VALUES(account_id),endpoint=VALUES(endpoint),p256dh=VALUES(p256dh),auth_secret=VALUES(auth_secret),user_agent=VALUES(user_agent),revoked_at=NULL")
    void upsert(
            @Param("accountId") long accountId,
            @Param("hash") byte[] hash,
            @Param("endpoint") String endpoint,
            @Param("p256dh") String p256dh,
            @Param("auth") String auth,
            @Param("agent") String agent);

    @Update(
            "UPDATE push_subscription SET revoked_at=UTC_TIMESTAMP(6) WHERE account_id=#{accountId} AND endpoint_hash=#{hash} AND revoked_at IS NULL")
    int revoke(@Param("accountId") long accountId, @Param("hash") byte[] hash);

    @Select(
            "SELECT subscription_id,endpoint,p256dh,auth_secret FROM push_subscription "
                    + "WHERE account_id=#{accountId} AND revoked_at IS NULL ORDER BY subscription_id")
    List<SubscriptionRow> findActive(long accountId);

    @Update(
            "UPDATE push_subscription SET last_success_at=#{now} WHERE subscription_id=#{subscriptionId} AND revoked_at IS NULL")
    void markSuccess(@Param("subscriptionId") long subscriptionId, @Param("now") LocalDateTime now);

    @Update(
            "UPDATE push_subscription SET revoked_at=#{now} WHERE subscription_id=#{subscriptionId} AND revoked_at IS NULL")
    void revokeById(@Param("subscriptionId") long subscriptionId, @Param("now") LocalDateTime now);

    record SubscriptionRow(
            long subscriptionId, String endpoint, String p256dh, String authSecret) {}
}
