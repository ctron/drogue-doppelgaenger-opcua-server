package io.drogue.doppelgaenger.opcua.server;

import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_ConfigurationError;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.drogue.doppelgaenger.opcua.ThingsSubscriptionManager;
import io.drogue.doppelgaenger.opcua.client.Client;
import io.drogue.doppelgaenger.opcua.milo.AllowAllServerCertificateValidator;
import io.drogue.doppelgaenger.opcua.milo.BuildInfoLoader;
import io.drogue.doppelgaenger.opcua.milo.KeyCertMaterial;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private static final String DEFAULT_APPLICATION_URI = "https://drogue.io/doppelgaenger/opcua";

    private static final String DEFAULT_PRODUCT_URI = "https://drogue.io";

    private static final String NAME = "Drogue Doppelgänger OPC UA integration";

    @ConfigMapping(prefix = "drogue.doppelgaenger.opcua")
    public interface Configuration {

        Optional<Set<String>> hostnames();

        @WithDefault("localhost")
        String bindAddress();

        @WithDefault("4840")
        int bindPort();

        @WithDefault("false")
        boolean enableAnonymous();

        Map<String, String> users();

        Optional<SelfSignedKey> selfSignedKey();

        Optional<ServerKey> serverKey();

        @WithDefault(DEFAULT_PRODUCT_URI)
        String productUri();

        @WithDefault("false")
        boolean acceptAllClientCertificates();

        @WithDefault("/tmp/milo/pki")
        Path pkiDirectory();
    }

    public enum SelfSignedMode {
        Persistent,
        Ephemeral,
    }

    public interface SelfSignedKey {
        @WithDefault("ephemeral")
        SelfSignedMode mode();

        @WithDefault("target/self-signed.pkcs")
        Path location();
    }

    public interface ServerKey {
        Path keystore();

        @WithDefault("PKCS12")
        String type();

        Optional<String> storePassword();

        Optional<String> keyPassword();

        String keyAlias();

        Optional<String> certificateAlias();
    }

    public static class Builder {

        private final Configuration configuration;

        public Builder(final Configuration configuration) {
            this.configuration = configuration;
        }

        Set<String> createHostnames() {
            return this.configuration.hostnames().orElseGet(() -> {
                final var r = new LinkedHashSet<String>();
                r.add(HostnameUtil.getHostname());
                r.addAll(HostnameUtil.getHostnames("0.0.0.0"));
                r.addAll(HostnameUtil.getHostnames("::1"));
                return r;
            });
        }

        Set<EndpointConfiguration> createEndpoints(final Optional<X509Certificate> certificate, final Set<String> hostnames) {
            final var result = new LinkedHashSet<EndpointConfiguration>();

            Server.logger.info("Announcing hostnames: {}", hostnames);

            for (final var hostname : hostnames) {
                buildEndpoint(hostname, certificate, result::add);
            }

            return result;
        }

        void buildEndpoint(final String hostname, final Optional<X509Certificate> certificate, final Consumer<EndpointConfiguration> consumer) {

            final var builder = EndpointConfiguration.newBuilder()
                    .setBindAddress(this.configuration.bindAddress())
                    .setBindPort(this.configuration.bindPort())
                    .setHostname(hostname)
                    .setPath("/drogue-iot")
                    .setTransportProfile(TransportProfile.TCP_UASC_UABINARY);

            certificate.ifPresent(builder::setCertificate);

            if (!this.configuration.users().isEmpty()) {
                builder
                        .addTokenPolicy(USER_TOKEN_POLICY_USERNAME);
            }

            if (this.configuration.enableAnonymous()) {
                builder
                        .addTokenPolicy(USER_TOKEN_POLICY_ANONYMOUS);
            }

            // no security
            consumer.accept(
                    builder.copy()
                            .setSecurityPolicy(SecurityPolicy.None)
                            .setSecurityMode(MessageSecurityMode.None)
                            .build()
            );

            // default security
            if (certificate.isPresent()) {
                consumer.accept(
                        builder.copy()
                                .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                                .setSecurityMode(MessageSecurityMode.SignAndEncrypt)
                                .build()
                );
            }

            // no security - discovery
            consumer.accept(
                    builder.copy()
                            .setPath("/drogue-iot/discovery")
                            .setSecurityPolicy(SecurityPolicy.None)
                            .setSecurityMode(MessageSecurityMode.None)
                            .build()
            );

        }

        Optional<KeyCertMaterial> createKeyCertMaterial(final Set<String> hostnames) throws Exception {

            if (this.configuration.serverKey().isPresent()) {
                final var sk = this.configuration.serverKey().get();
                return Optional.of(KeyCertMaterial.load(
                        sk.keystore(),
                        sk.type(),
                        sk.storePassword().map(String::toCharArray).orElse(null),
                        sk.keyAlias(),
                        sk.keyPassword().map(String::toCharArray).orElse(null),
                        sk.certificateAlias().orElse(sk.keyAlias())
                ));
            }

            if (this.configuration.selfSignedKey().isPresent()) {
                final var ssk = this.configuration.selfSignedKey().get();

                KeyCertMaterial kc = null;

                switch (ssk.mode()) {
                case Persistent: {
                    kc = KeyCertMaterial.loadOrCreateSelfSigned(
                            ssk.location(),
                            hostnames,
                            Server.DEFAULT_APPLICATION_URI
                    );
                    break;
                }
                case Ephemeral: {
                    kc = KeyCertMaterial.createSelfSigned(
                            hostnames,
                            Server.DEFAULT_APPLICATION_URI
                    );
                    break;
                }

                }

                return Optional.of(kc);

            }

            // no key/cert material

            return Optional.empty();

        }

        public CompletableFuture<Server> start(
                final Client client,
                final ThingsSubscriptionManager subscriptions
        ) throws Exception {

            Objects.requireNonNull(client);
            Objects.requireNonNull(subscriptions);

            // core information

            if (this.configuration.enableAnonymous()) {
                logger.warn("Enabling anonymous authentication");
            } else if (this.configuration.users().isEmpty()) {
                logger.warn("No authentication options configured. All connection attempts will be rejected.");
            }

            logger.info("Drogue IoT - Doppelgaenger OPC UA server");

            // build information

            final var buildInfoBuilder = BuildInfo.builder()
                    .productName(NAME)
                    .productUri(this.configuration.productUri())
                    .manufacturerName("Drogue IoT");

            BuildInfoLoader.load(buildInfoBuilder);

            final var buildInfo = buildInfoBuilder.build();

            logger.info("    Manufacturer Name: {}", buildInfo.getManufacturerName());
            logger.info("    Product URI:       {}", buildInfo.getProductUri());
            logger.info("    Product Name:      {}", buildInfo.getProductName());
            logger.info("    Version:           {}", buildInfo.getSoftwareVersion());
            logger.info("    Build Number:      {}", buildInfo.getBuildNumber());
            logger.info("    Build Date:        {}", buildInfo.getBuildDate());

            logger.info("Binding to: {}:{}", this.configuration.bindAddress(), this.configuration.bindPort());

            final var hostnames = createHostnames();

            // load key & cert

            final var loader = createKeyCertMaterial(hostnames);

            final var certificateManager = loader.map(l -> {
                final var cm = new DefaultCertificateManager(
                        l.getServerKeyPair(),
                        l.getServerCertificateChain()
                );

                cm.getCertificates().forEach(c -> {
                    String san;
                    try {
                        san = String.format("%s", c.getSubjectAlternativeNames());
                    } catch (final Exception e) {
                        san = String.format("<error: %s>", e);
                    }
                    logger.info("Certificate: {}: {}", c.getSubjectX500Principal(), san);
                });

                return cm;
            });

            // validators

            final var validators = new LinkedList<IdentityValidator<String>>();
            if (!this.configuration.users().isEmpty()) {
                final var users = this.configuration.users();
                Server.logger.info("Known users: {}", users.size());
                validators.add(new UsernameIdentityValidator(this.configuration.enableAnonymous(), auth -> {
                    Server.logger.info("Authenticating: {}", auth.getUsername());
                    final var pwd = users.get(auth.getUsername());
                    return pwd != null && pwd.equals(auth.getPassword());
                }));
            } else if (this.configuration.enableAnonymous()) {
                validators.add(AnonymousIdentityValidator.INSTANCE);
            }

            final var certificate = certificateManager.map(cm -> cm.getCertificates()
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new UaRuntimeException(Bad_ConfigurationError, "no certificate found")));

            final var applicationUri = certificate.map(c ->
                            CertificateUtil
                                    .getSanUri(c)
                                    .orElseThrow(() -> new UaRuntimeException(Bad_ConfigurationError, "certificate is missing the application URI"))
                    )
                    .orElse(Server.DEFAULT_APPLICATION_URI);

            // endpoints

            final var endpoints = createEndpoints(certificate, hostnames);

            // server

            final var config = OpcUaServerConfig.builder()
                    .setApplicationUri(applicationUri)
                    .setApplicationName(LocalizedText.english(NAME))
                    .setBuildInfo(buildInfo)
                    .setProductUri(this.configuration.productUri())
                    .setIdentityValidator(new CompositeValidator<>(validators))
                    .setEndpoints(endpoints);

            certificateManager.ifPresent(cm -> {
                config.setCertificateManager(cm);

                if (this.configuration.acceptAllClientCertificates()) {

                    final var certificateValidator =
                            new AllowAllServerCertificateValidator();

                    config
                            .setCertificateValidator(certificateValidator);

                } else {

                    final DefaultTrustListManager trustListManager;
                    try {
                        trustListManager = new DefaultTrustListManager(this.configuration.pkiDirectory().toFile());
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }

                    final var certificateValidator =
                            new DefaultServerCertificateValidator(trustListManager);

                    config
                            .setTrustListManager(trustListManager)
                            .setCertificateValidator(certificateValidator);
                }

            });

            // create server

            final var server = new OpcUaServer(config.build());

            // register namespaces

            final var propertyNamespace = new PropertyNamespace(server, subscriptions);
            server.getAddressSpaceManager()
                    .register(propertyNamespace);

            final var namespace = new ThingNamespace(server, propertyNamespace, client);
            server.getAddressSpaceManager()
                    .register(namespace);

            // startup

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
