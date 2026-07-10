package com.langxi.babydiary.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiChatMessage {
    private String role;
    private String content;
}
