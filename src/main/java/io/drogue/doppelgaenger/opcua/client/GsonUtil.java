package io.drogue.doppelgaenger.opcua.client;

import java.time.OffsetDateTime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

public final class GsonUtil {
    private GsonUtil() {
    }

    public static Gson create() {
        return new GsonBuilder()
                .registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
                        (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
                .create();
    }
}
