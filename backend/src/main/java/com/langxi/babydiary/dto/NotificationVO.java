package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.AppNotification;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class NotificationVO {
    private String publicId;
    private String type;
    private String title;
    private String body;
    private String targetPath;
    private Timestamp readAt;
    private Timestamp createdAt;

    public static NotificationVO from(AppNotification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setPublicId(notification.getPublicId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setBody(notification.getBody());
        vo.setTargetPath(notification.getTargetPath());
        vo.setReadAt(notification.getReadAt());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }
}
