package com.example.capma.data;

import androidx.room.TypeConverter;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Type converter for Room database to store float arrays
 */
public class FloatArrayConverter {
    
    @TypeConverter
    public static byte[] fromFloatArray(float[] floatArray) {
        if (floatArray == null) {
            return null;
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(floatArray.length * 4);
        for (float value : floatArray) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }
    
    @TypeConverter
    public static float[] toFloatArray(byte[] byteArray) {
        if (byteArray == null) {
            return null;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        float[] floatArray = new float[byteArray.length / 4];
        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] = buffer.getFloat();
        }
        return floatArray;
    }
} 