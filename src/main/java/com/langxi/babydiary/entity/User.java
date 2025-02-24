package com.langxi.babydiary.entity;

import lombok.Data;
import java.sql.Timestamp;

@Data
public class User {
    private Integer userId;
    private String username;
    private String password;
    private Timestamp createdAt;

    // Getters and Setters
}
