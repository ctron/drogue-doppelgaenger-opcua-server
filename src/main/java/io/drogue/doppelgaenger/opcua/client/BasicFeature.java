package io.drogue.doppelgaenger.opcua.client;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class BasicFeature {
    private OffsetDateTime lastUpdate;

    private JsonElement value;

    public JsonElement getValue() {
        return this.value;
    }

    public OffsetDateTime getLastUpdate() {
        return this.lastUpdate;
    }

    public void setLastUpdate(final OffsetDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public void setValue(final JsonElement value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("lastUpdate", this.lastUpdate)
                .add("value", this.value)
                .toString();
    }

    @SuppressWarnings("SameParameterValue")
    public <T> Optional<T> toTyped(final Class<T> clazz) {
        final var value = this.value;
        if (value == null) {
            return empty();
        }
        if (!clazz.isAssignableFrom(value.getClass())) {
            return empty();
        }

        return of(clazz.cast(value));
    }

    public Optional<JsonObject> asObject() {
        if (this.value.isJsonObject()) {
            return of(this.value.getAsJsonObject());
        } else {
            return empty();
        }
    }

}
