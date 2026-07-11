package com.langxi.babydiary.service;

public record PushNotificationEvent(Integer userId, String title, String body, String targetPath) {
}
