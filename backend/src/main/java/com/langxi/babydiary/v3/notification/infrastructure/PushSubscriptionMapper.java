package com.langxi.babydiary.v3.notification.infrastructure;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PushSubscriptionMapper {
@Insert("INSERT INTO push_subscription(account_id,endpoint_hash,endpoint,p256dh,auth_secret,user_agent) VALUES(#{accountId},#{hash},#{endpoint},#{p256dh},#{auth},#{agent}) "+
"ON DUPLICATE KEY UPDATE account_id=VALUES(account_id),endpoint=VALUES(endpoint),p256dh=VALUES(p256dh),auth_secret=VALUES(auth_secret),user_agent=VALUES(user_agent),revoked_at=NULL")
void upsert(@Param("accountId")long accountId,@Param("hash")byte[] hash,@Param("endpoint")String endpoint,@Param("p256dh")String p256dh,@Param("auth")String auth,@Param("agent")String agent);
@Update("UPDATE push_subscription SET revoked_at=UTC_TIMESTAMP(6) WHERE account_id=#{accountId} AND endpoint_hash=#{hash} AND revoked_at IS NULL")
int revoke(@Param("accountId")long accountId,@Param("hash")byte[] hash);}
