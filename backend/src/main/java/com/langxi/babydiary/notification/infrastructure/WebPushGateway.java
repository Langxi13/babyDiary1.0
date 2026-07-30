package com.langxi.babydiary.notification.infrastructure;

import com.langxi.babydiary.notification.application.PushGateway;
import com.langxi.babydiary.notification.application.PushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebPushGateway implements PushGateway {
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushGateway(
            @Value("${app.push.vapid-public-key:}") String publicKey,
            @Value("${app.push.vapid-private-key:}") String privateKey,
            @Value("${app.push.subject:mailto:admin@example.com}") String subject) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    @Override
    public boolean configured() {
        return publicKey != null
                && !publicKey.isBlank()
                && privateKey != null
                && !privateKey.isBlank();
    }

    @Override
    public int send(PushSubscriptionRepository.Subscription subscription, String payload)
            throws Exception {
        if (!configured()) return 204;
        PushService service = new PushService(publicKey, privateKey, subject);
        HttpResponse response =
                service.send(
                        new Notification(
                                subscription.endpoint(),
                                subscription.p256dh(),
                                subscription.authSecret(),
                                payload));
        return response.getStatusLine().getStatusCode();
    }
}
