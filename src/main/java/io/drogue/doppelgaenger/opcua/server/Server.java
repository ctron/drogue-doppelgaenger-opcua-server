package io.drogue.doppelgaenger.opcua.server;

import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.drogue.doppelgaenger.opcua.ThingsSubscriptionManager;
import io.drogue.doppelgaenger.opcua.client.Client;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private static final String APPLICATION_URI = "https://drogue.io";

    private static final String PRODUCT_URI = "https://drogue.io";

    private static final String NAME = "Drogue Doppelgänger OPC UA integration";

    public static class Builder {

        private final Client client;

        private Set<String> hostnames = null;

        private final String bindAddress = "localhost";

        private final int bindPort = 4840;

        final ThingsSubscriptionManager subscriptions;

        public Builder(final Client client, final ThingsSubscriptionManager subscriptions) {
            this.client = client;
            this.subscriptions = subscriptions;
        }

        public Builder hostnames(final Set<String> hostnames) {
            this.hostnames = hostnames;
            return this;
        }

        Set<EndpointConfiguration> createEndpoints() {
            final var result = new LinkedHashSet<EndpointConfiguration>();

            final Set<String> hostnames;
            if (this.hostnames != null) {
                hostnames = this.hostnames;
            } else {
                hostnames = new LinkedHashSet<>();
                hostnames.add(HostnameUtil.getHostname());
                hostnames.addAll(HostnameUtil.getHostnames("0.0.0.0"));
                hostnames.addAll(HostnameUtil.getHostnames("::1"));
            }

            Server.logger.info("Announcing hostnames: {}", hostnames);

            for (final var hostname : hostnames) {
                buildEndpoint(hostname, result::add);
            }

            return result;
        }

        void buildEndpoint(final String hostname, final Consumer<EndpointConfiguration> consumer) {
            final var builder = EndpointConfiguration.newBuilder()
                    .setBindAddress(this.bindAddress)
                    .setBindPort(this.bindPort)
                    .setHostname(hostname)
                    .setPath("/drogue-iot")
                    .setTransportProfile(TransportProfile.TCP_UASC_UABINARY);

            // FIXME: allow username/password
            builder
                    .addTokenPolicy(USER_TOKEN_POLICY_ANONYMOUS);

            // no security
            consumer.accept(builder.copy()
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None)
                    .build());

            // FIXME: enable
            // default security
            //consumer.accept(
            //        builder.copy()
            //                .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
            //                .setSecurityMode(MessageSecurityMode.SignAndEncrypt)
            //                .build()
            //);

            // no security - discovery
            consumer.accept(
                    builder.copy()
                            .setPath("/drogue-iot/discovery")
                            .setSecurityPolicy(SecurityPolicy.None)
                            .setSecurityMode(MessageSecurityMode.None)
                            .build()
            );

        }

        public CompletableFuture<Server> start() {

            final var validators = new LinkedList<IdentityValidator<String>>();
            // FIXME: need to use a real one
            validators.push(AnonymousIdentityValidator.INSTANCE);

            // endpoints

            final var endpoints = createEndpoints();

            // server

            final var config = OpcUaServerConfig.builder()
                    .setApplicationUri(Server.APPLICATION_URI)
                    .setApplicationName(LocalizedText.english(Server.NAME))
                    .setBuildInfo(new BuildInfo(
                            Server.PRODUCT_URI,
                            "Drogue IoT",
                            Server.NAME,
                            OpcUaServer.SDK_VERSION,
                            "0",
                            new DateTime(Instant.parse("2022-09-23T08:47:00Z")))
                    )
                    .setProductUri(Server.PRODUCT_URI)
                    .setIdentityValidator(new CompositeValidator<>(validators))
                    .setEndpoints(endpoints)
                    .build();

            final var server = new OpcUaServer(config);

            final var propertyNamespace = new PropertyNamespace(server, this.subscriptions);
            server.getAddressSpaceManager()
                    .register(propertyNamespace);

            final var namespace = new ThingNamespace(server, propertyNamespace, this.client);
            server.getAddressSpaceManager()
                    .register(namespace);

            return server.startup()
                    .thenApply(Server::new);
        }
    }

    private final OpcUaServer server;

    private Server(final OpcUaServer server) {
        this.server = server;
    }

    public CompletableFuture<Void> close() {
        return this.server.shutdown()
                .thenApply(ignore -> null);
    }
}
