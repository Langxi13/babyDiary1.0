package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.PrivateShare;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class PrivateShareSummaryVO {
    private String shareId;
    private Timestamp expiresAt;
    private Integer maxViews;
    private Integer viewCount;
    private boolean passwordProtected;
    private Timestamp createdAt;

    public static PrivateShareSummaryVO from(PrivateShare share) {
        PrivateShareSummaryVO value = new PrivateShareSummaryVO();
        value.setShareId(share.getPublicId());
        value.setExpiresAt(share.getExpiresAt());
        value.setMaxViews(share.getMaxViews());
        value.setViewCount(share.getViewCount());
        value.setPasswordProtected(share.getPasswordHash() != null);
        value.setCreatedAt(share.getCreatedAt());
        return value;
    }
}
