package io.drogue.doppelgaenger.opcua;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.drogue.doppelgaenger.opcua.client.Client;
import io.drogue.doppelgaenger.opcua.client.OidcAuthenticationProvider;
import io.drogue.doppelgaenger.opcua.server.Server;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.runtime.Startup;
import io.vertx.core.Vertx;

@Startup
public class Application {

    private final AtomicReference<Server> server = new AtomicReference<>();

    @Inject
    OidcClient oidcClient;

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "drogue.doppelgaenger.api")
    String api;

    @ConfigProperty(name = "drogue.doppelgaenger.application", defaultValue = "default")
    String application;

    private Client client;

    private ThingsSubscriptionManager subscriptions;

    @PostConstruct
    public void start() throws Exception {
        final var provider = new OidcAuthenticationProvider(this.oidcClient);

        this.client = new Client(
                this.vertx,
                this.api,
                this.application,
                provider);

        this.subscriptions = new ThingsSubscriptionManager(this.vertx, URI.create(this.api), this.application, provider);

        this.server.set(new Server.Builder(this.client, this.subscriptions)
                .start()
                .get());
    }

    @PreDestroy
    public void stop() throws Exception {
        final var server = this.server.getAndSet(null);
        if (server != null) {
            server.close()
                    .get();
        }
        this.client.close();
        this.subscriptions.close();
    }

}
