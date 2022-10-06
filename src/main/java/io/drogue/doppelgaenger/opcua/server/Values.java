package io.drogue.doppelgaenger.opcua.server;

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import com.google.gson.JsonElement;

import io.drogue.doppelgaenger.opcua.client.BasicFeature;

public final class Values {

    private Values() {
    }

    public static DataValue toDataValue(
            @NonNull final Optional<BasicFeature> feature) {
        return toDataValue(feature.orElse(null));
    }

    public static DataValue toDataValue(@Nullable final BasicFeature feature) {
        if (feature != null) {
            return new DataValue(
                    toVariant(feature.getValue()),
                    StatusCode.GOOD,
                    new DateTime(feature.getLastUpdate().toInstant())
            );
        } else {
            return new DataValue(StatusCode.UNCERTAIN);
        }

    }

    /**
     * Convert a Doppelgaenger value (JSON) into an OPC UA variant type.
     *
     * @param value The JSON value.
     * @return The variant type.
     */
    public static Variant toVariant(@Nullable final JsonElement value) {

        if (value == null || value.isJsonNull()) {
            return new Variant(null);
        }

        if (value.isJsonPrimitive()) {
            final var p = value.getAsJsonPrimitive();
            if (p.isNumber()) {
                return new Variant(p.getAsNumber().doubleValue());
            } else if (p.isBoolean()) {
                return new Variant(p.getAsBoolean());
            } else {
                return new Variant(p.getAsString());
            }
        }

        if (value.isJsonArray()) {
            final var items = value.getAsJsonArray();
            final var array = new Variant[items.size()];
            var i = 0;
            for (final var item : items) {
                array[i] = toVariant(item);
                i++;
            }
            return new Variant(array);
        }

        return new Variant(value.toString());

    }

}
