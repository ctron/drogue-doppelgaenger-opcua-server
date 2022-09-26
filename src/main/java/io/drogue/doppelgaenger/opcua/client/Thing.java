package io.drogue.doppelgaenger.opcua.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.base.MoreObjects;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Thing {
    private Metadata metadata;

    private Map<String, ReportedFeature> reportedState = new HashMap<>();

    private Map<String, SyntheticFeature> syntheticState = new HashMap<>();

    public static class Builder {
        private final Thing thing;

        public Builder(final String name) {
            this.thing = new Thing(new Metadata(name));
        }

        public Thing build() {
            return new Thing(this.thing);
        }
    }

    Thing() {
    }

    public Thing(final Metadata metadata) {
        this.metadata = Objects.requireNonNull(metadata);
    }

    public Thing(final Thing other) {
        Objects.requireNonNull(other);

        this.metadata = new Metadata(other.metadata);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("metadata", this.metadata)
                .add("reportedState", this.reportedState)
                .add("syntheticState", this.syntheticState)
                .toString();
    }

    public void setMetadata(final Metadata metadata) {
        this.metadata = metadata;
    }

    public Metadata getMetadata() {
        return this.metadata;
    }

    public Map<String, ReportedFeature> getReportedState() {
        return this.reportedState;
    }

    public void setReportedState(final Map<String, ReportedFeature> reportedState) {
        this.reportedState = reportedState;
    }

    public Map<String, SyntheticFeature> getSyntheticState() {
        return this.syntheticState;
    }

    public void setSyntheticState(final Map<String, SyntheticFeature> syntheticState) {
        this.syntheticState = syntheticState;
    }

    public Map<String, BasicFeature> mergedState() {
        final var result = new HashMap<String, BasicFeature>();

        result.putAll(this.reportedState);
        result.putAll(this.syntheticState);

        return result;
    }
}
