package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class SpaceInvitation {
    private Long invitationId;
    private Long spaceId;
    private Integer invitedBy;
    private String email;
    private String tokenHash;
    private String role;
    private String status;
    private Timestamp expiresAt;
    private Integer acceptedBy;
    private Timestamp createdAt;
}
