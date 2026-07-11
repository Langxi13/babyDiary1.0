package com.langxi.babydiary.storage;

import io.minio.*;
import io.minio.errors.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;

    public S3ObjectStorage(@Value("${app.storage.s3.endpoint}") String endpoint,
                           @Value("${app.storage.s3.region:us-east-1}") String region,
                           @Value("${app.storage.s3.bucket}") String bucket,
                           @Value("${app.storage.s3.access-key}") String accessKey,
                           @Value("${app.storage.s3.secret-key}") String secretKey) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).region(region).build();
        this.bucket = bucket;
    }

    @Override
    public String provider() {
        return "S3";
    }

    @Override
    public void put(String key, InputStream input, long size, String contentType) throws IOException {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(input, size, -1).contentType(contentType).build());
        } catch (Exception exception) {
            throw io(exception);
        }
    }

    @Override
    public StoredObject get(String key) throws IOException {
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            InputStream stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
            return new StoredObject(stream, stat.size(), stat.contentType());
        } catch (Exception exception) {
            throw io(exception);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception exception) {
            throw io(exception);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    private IOException io(Exception exception) {
        return exception instanceof IOException value ? value : new IOException("S3 object operation failed", exception);
    }
}
