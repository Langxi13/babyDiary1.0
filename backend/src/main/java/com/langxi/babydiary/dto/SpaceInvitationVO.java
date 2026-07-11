package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
public class SpaceInvitationVO {
    private String token;
    private Timestamp expiresAt;
}
