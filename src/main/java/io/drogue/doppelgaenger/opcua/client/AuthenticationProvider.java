package io.drogue.doppelgaenger.opcua.client;

import java.util.concurrent.CompletableFuture;

import io.quarkus.oidc.client.Tokens;
import io.vertx.ext.web.client.HttpRequest;

public interface AuthenticationProvider {

    <T> CompletableFuture<HttpRequest<T>> inject(HttpRequest<T> request);

    CompletableFuture<Tokens> getBearerToken();

}
