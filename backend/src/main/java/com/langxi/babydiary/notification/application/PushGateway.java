package com.langxi.babydiary.notification.application;

public interface PushGateway {
    boolean configured();

    int send(PushSubscriptionRepository.Subscription subscription, String payload) throws Exception;
}
