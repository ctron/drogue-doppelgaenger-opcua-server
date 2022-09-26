package io.drogue.doppelgaenger.opcua.client;

import java.util.Objects;

import com.google.common.base.MoreObjects;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Metadata {
    private String name;

    Metadata() {
    }

    public Metadata(final String name) {
        this.name = Objects.requireNonNull(name);
    }

    public Metadata(final Metadata other) {
        Objects.requireNonNull(other);

        this.name = other.name;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", this.name)
                .toString();
    }
}
