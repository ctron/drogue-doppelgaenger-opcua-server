package io.drogue.doppelgaenger.opcua.client;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.core.UriBuilder;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class Client implements AutoCloseable {

    private final String api;

    private final String application;

    private final AuthenticationProvider authenticationProvider;

    private final WebClient client;

    public Client(final Vertx vertx, final String api, final String application, final AuthenticationProvider authenticationProvider) {
        this.api = api;
        this.application = application;
        this.authenticationProvider = authenticationProvider;
        final WebClientOptions options = new WebClientOptions();
        this.client = WebClient.create(vertx, options);
    }

    @Override
    public void close() {
        this.client.close();
    }

    public CompletableFuture<Optional<Thing>> get(final String name) {

        final var url = UriBuilder.fromUri(this.api)
                .path("/api/v1alpha1/things/{application}/things/{thing}")
                .build(this.application, name);

        final var request = this.client.getAbs(url.toString());

        return this.authenticationProvider.inject(request)
                .thenCompose(req -> {
                    return req.send()
                            .<Optional<Thing>>map(response -> {

                                switch (response.statusCode()) {
                                case 404:
                                    return Optional.empty();
                                case 200:
                                    return Optional.ofNullable(GsonUtil.create().fromJson(response.bodyAsString(), Thing.class));
                                default:
                                    throw new RuntimeException("Unexpected status code: " + response.statusCode());
                                }

                            })
                            .toCompletionStage();
                });

    }

}
