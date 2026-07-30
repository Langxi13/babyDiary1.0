package com.langxi.babydiary.platform.infrastructure;

import com.langxi.babydiary.platform.application.SecretCodec;
import com.langxi.babydiary.platform.application.SecretCodecFactory;
import org.springframework.stereotype.Component;

@Component
public class AesGcmSecretCodecFactory implements SecretCodecFactory {
    @Override
    public SecretCodec create(String encryptionKey) {
        return new AesGcmSecretCodec(encryptionKey);
    }
}
