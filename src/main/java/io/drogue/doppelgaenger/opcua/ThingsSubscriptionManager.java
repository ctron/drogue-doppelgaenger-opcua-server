package io.drogue.doppelgaenger.opcua;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.drogue.doppelgaenger.opcua.client.OidcAuthenticationProvider;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;

public class ThingsSubscriptionManager {

    private final HttpClient client;

    public interface Subscription {
        void close();
    }

    private final Map<String, Thing> listeners = new HashMap<>();

    private final Lock listenerLock = new ReentrantLock();

    private final Vertx vertx;

    private final URI api;

    private final String application;

    private final OidcAuthenticationProvider provider;

    class Thing {

        private final ThingListener listener;

        private final Map<Object, ThingListener.Listener> listeners = new HashMap<>();

        private Optional<io.drogue.doppelgaenger.opcua.client.Thing> lastState = Optional.empty();

        Thing(final String thing) {
            this.listener = new ThingListener(ThingsSubscriptionManager.this.vertx,
                    ThingsSubscriptionManager.this.client,
                    ThingsSubscriptionManager.this.api,
                    ThingsSubscriptionManager.this.application,
                    thing,
                    ThingsSubscriptionManager.this.provider,
                    this::onStateChange);
        }

        void close() {
            this.listener.close();
        }

        void onStateChange(final Optional<io.drogue.doppelgaenger.opcua.client.Thing> state) {
            this.lastState = state;
            this.listeners.values().forEach(l -> l.onChange(state));
        }

        public Object attach(final ThingListener.Listener listener) {
            final var handle = new Object();
            listener.onChange(this.lastState);
            this.listeners.put(handle, listener);
            return handle;
        }

        public boolean detach(final Object handle) {
            this.listeners.remove(handle);
            return this.listeners.isEmpty();
        }
    }

    public ThingsSubscriptionManager(final Vertx vertx, final URI api, final String application, final OidcAuthenticationProvider provider) {
        this.vertx = vertx;
        this.client = vertx.createHttpClient(new HttpClientOptions());
        this.api = api;
        this.application = application;
        this.provider = provider;
    }

    public Subscription createSubscription(final String thing, final String name, final ThingListener.Listener listener) {

        try {
            this.listenerLock.lock();

            final var t = this.listeners.computeIfAbsent(thing, x -> {
                return new Thing(thing);
            });

            final var handle = t.attach(listener);

            return new Subscription() {
                @Override
                public void close() {
                    detachSubscription(thing, handle);
                }
            };

        } finally {
            this.listenerLock.unlock();
        }

    }

    void detachSubscription(final String thingName, final Object handle) {
        try {
            this.listenerLock.lock();

            final var t = this.listeners.get(thingName);
            if (t != null && t.detach(handle)) {
                this.listeners.remove(thingName);
                t.close();
            }

        } finally {
            this.listenerLock.unlock();
        }
    }

    public void close() {
        this.listeners.values().forEach(Thing::close);
        this.listeners.clear();
        this.client.close();
    }

}
