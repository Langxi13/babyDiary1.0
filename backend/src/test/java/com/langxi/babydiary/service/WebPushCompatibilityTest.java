package com.langxi.babydiary.service;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushCompatibilityTest {
    @Test
    void upgradedCryptoDependenciesCanBuildVapidRequest() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPair serverKeys = keyPair();
        KeyPair userKeys = keyPair();
        Notification notification = new Notification(
                "https://push.example.com/subscription",
                userKeys.getPublic(),
                new byte[16],
                "test".getBytes(StandardCharsets.UTF_8),
                60);

        PushService service = new PushService(serverKeys, "mailto:admin@example.com");
        HttpPost request = service.preparePost(notification, Encoding.AES128GCM);

        assertThat(request.getFirstHeader("Authorization").getValue()).contains("vapid");
        assertThat(request.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
