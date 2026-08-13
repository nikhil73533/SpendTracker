package com.example.prediction.util;

import androidx.room.TypeConverter;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class Converters {
    @TypeConverter
    public static float[] fromBlob(byte[] bytes) {
        if (bytes == null) return null;
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        FloatBuffer fb = bb.asFloatBuffer();
        float[] floats = new float[fb.limit()];
        fb.get(floats);
        return floats;
    }

    @TypeConverter
    public static byte[] toBlob(float[] floats) {
        if (floats == null) return null;
        ByteBuffer bb = ByteBuffer.allocate(floats.length * 4);
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(floats);
        return bb.array();
    }
}
