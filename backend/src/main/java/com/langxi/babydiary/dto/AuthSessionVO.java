package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.AuthSession;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class AuthSessionVO {
    private String publicId;
    private String deviceName;
    private String ipAddress;
    private Timestamp lastSeenAt;
    private Timestamp expiresAt;
    private Timestamp createdAt;
    private boolean current;

    public static AuthSessionVO from(AuthSession session, String currentSessionId) {
        AuthSessionVO vo = new AuthSessionVO();
        vo.setPublicId(session.getPublicId());
        vo.setDeviceName(session.getDeviceName());
        vo.setIpAddress(session.getIpAddress());
        vo.setLastSeenAt(session.getLastSeenAt());
        vo.setExpiresAt(session.getExpiresAt());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setCurrent(session.getPublicId().equals(currentSessionId));
        return vo;
    }
}
