package io.drogue.doppelgaenger.opcua.client;

import java.util.concurrent.CompletableFuture;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.vertx.ext.web.client.HttpRequest;

public class OidcAuthenticationProvider implements AuthenticationProvider {

    private final OidcClient client;

    public OidcAuthenticationProvider(final OidcClient client) {
        this.client = client;
    }

    @Override
    public <T> CompletableFuture<HttpRequest<T>> inject(final HttpRequest<T> request) {
        return this.client.getTokens().subscribeAsCompletionStage()
                .thenApply(tokens -> {
                    return request
                            .bearerTokenAuthentication(tokens.getAccessToken());
                });
    }

    @Override
    public CompletableFuture<Tokens> getBearerToken() {
        return this.client.getTokens().subscribeAsCompletionStage();
    }
}
