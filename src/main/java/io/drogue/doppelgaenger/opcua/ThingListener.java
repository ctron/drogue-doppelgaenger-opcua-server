package io.drogue.doppelgaenger.opcua;

import static io.vertx.core.Future.fromCompletionStage;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.drogue.doppelgaenger.opcua.client.AuthenticationProvider;
import io.drogue.doppelgaenger.opcua.client.GsonUtil;
import io.drogue.doppelgaenger.opcua.client.Thing;
import io.quarkus.oidc.client.Tokens;
import io.vertx.core.Context;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebsocketVersion;

public class ThingListener {

    private static final Logger logger = LoggerFactory.getLogger(ThingListener.class);

    @FunctionalInterface
    public interface Listener {
        void onChange(Optional<Thing> state);
    }

    private final Listener listener;

    private final Vertx vertx;

    private final HttpClient client;

    private final URI api;

    private final boolean secure;

    private final String application;

    private final String name;

    private final Context context;

    private final AuthenticationProvider provider;

    private final long timer;

    private volatile boolean closed;

    private WebSocket websocket;

    private Tokens tokens;

    public ThingListener(final Vertx vertx, final HttpClient client, final URI api, final String application, final String name, final AuthenticationProvider provider, final Listener listener) {

        this.secure = api.getScheme().equals("https");

        this.vertx = vertx;
        this.client = client;
        this.api = api;
        this.application = application;
        this.name = name;
        this.context = vertx.getOrCreateContext();
        this.provider = provider;
        this.listener = listener;

        this.context.runOnContext(x -> connect());
        this.timer = this.vertx.setPeriodic(5_000, x -> checkToken());
    }

    public void close() {
        this.closed = true;
        this.vertx.cancelTimer(this.timer);

        this.context.runOnContext(x -> {
            if (this.websocket != null) {
                this.websocket.close();
                this.websocket = null;
            }
        });
    }

    private void connect() {

        this.context.runOnContext(x -> {
            if (this.closed) {
                return;
            }

            fromCompletionStage(this.provider.getBearerToken())
                    .flatMap(tokens -> {
                        final var uri = getUri(tokens.getAccessToken());
                        logger.info("Connecting: {}", uri);
                        this.tokens = tokens;
                        return this.client.webSocketAbs(
                                uri.toString(),
                                MultiMap.caseInsensitiveMultiMap(),
                                WebsocketVersion.V13,
                                List.of()
                        );
                    })
                    .onSuccess(this::connected)
                    .onFailure(this::disconnected);

        });
    }

    private void disconnected(final Throwable throwable) {
        logger.info("Disconnected", throwable);
        this.websocket = null;
        if (!this.closed) {
            publish(null);
            this.vertx.setTimer(5_000, x -> connect());
        }
    }

    private void connected(final WebSocket websocket) {
        if (this.closed) {
            websocket.close();
            return;
        }
        websocket.textMessageHandler(this::handleMessage);
        websocket.closeHandler(x -> disconnected(null));
        this.websocket = websocket;
    }

    private void handleMessage(final String message) {
        try {

            final var gson = GsonUtil.create();

            final var json = gson.fromJson(message, com.google.gson.JsonObject.class);
            final var type = json.get("type").getAsString();

            if ("change".equals(type)) {
                final var thingJson = json.get("thing");
                final var thing = gson.fromJson(thingJson, Thing.class);
                logger.info("Update: {}", thing);
                publish(thing);
            } else if ("initial".equals(type)) {
                final var thingJson = json.get("thing");
                final var thing = gson.fromJson(thingJson, Thing.class);
                logger.info("Initial update: {}", thing);
                publish(thing);
            }
        } catch (final Exception e) {
            logger.info("Failed to process", e);
            publish(null);
            this.websocket.close();
        }
    }

    private void publish(final Thing state) {
        this.listener.onChange(Optional.ofNullable(state));
    }

    private URI getUri(final String accessToken) {
        return UriBuilder.fromUri(this.api)
                .scheme(this.secure ? "wss" : "ws")
                .path("/api/v1alpha1/things/{application}/things/{name}/notifications")
                .queryParam("token", accessToken)
                .build(this.application, this.name);
    }

    private void checkToken() {
        if (this.closed || this.tokens == null) {
            return;
        }
        if (this.tokens.isAccessTokenWithinRefreshInterval()) {
            this.provider.getBearerToken().thenAccept(this::setTokens);
        }
    }

    public void setTokens(final Tokens tokens) {
        this.tokens = tokens;
        // FIXME: right now, we don't need to refresh tokens ...
    }
}
