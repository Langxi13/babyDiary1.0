package com.langxi.babydiary.platform.application;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class BinaryUuid {
    private BinaryUuid() {}

    public static byte[] toBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID fromBytes(byte[] value) {
        if (value == null || value.length != 16)
            throw new IllegalArgumentException("Binary UUID must contain 16 bytes");
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
