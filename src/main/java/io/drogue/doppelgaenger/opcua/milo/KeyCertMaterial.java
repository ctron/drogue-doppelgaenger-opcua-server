package io.drogue.doppelgaenger.opcua.milo;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Set;

import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

public final class KeyCertMaterial {

    private static final Logger logger = LoggerFactory.getLogger(KeyCertMaterial.class);

    private final Material material;

    private static class Material {
        private final X509Certificate[] certificateChain;

        private final KeyPair keyPair;

        Material(final KeyPair keyPair, final X509Certificate[] certificateChain) {
            this.keyPair = keyPair;
            this.certificateChain = certificateChain;
        }
    }

    private KeyCertMaterial(final Material material) {
        this.material = material;
    }

    public X509Certificate[] getServerCertificateChain() {
        return this.material.certificateChain;
    }

    public KeyPair getServerKeyPair() {
        return this.material.keyPair;
    }

    /**
     * Create new, self-signed key-cert material.
     *
     * @param hostnames The hostnames (and IP addresses to add).
     * @return The newly created, self-signed key material.
     * @throws Exception If anything goes wrong.
     */
    public static KeyCertMaterial createSelfSigned(final Set<String> hostnames, final String applicationUri) throws Exception {

        final var keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);

        final var builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName("Drogue IoT")
                .setOrganization("Red Hat, Inc")
                .setLocalityName("Raleigh")
                .setStateName("NC")
                .setCountryCode("US")
                .setApplicationUri(applicationUri);

        for (final var hostname : hostnames) {
            try {
                InetAddresses.forString(hostname);
                builder.addIpAddress(hostname);
            } catch (final Exception e) {
                builder.addDnsName(hostname);
            }
        }

        return new KeyCertMaterial(new Material(keyPair, new X509Certificate[] { builder.build() }));
    }

    private static final char[] SELF_SIGNED_PASSWORD = "NotAtAllSecret".toCharArray();

    public static KeyCertMaterial loadOrCreateSelfSigned(final Path file, final Set<String> hostnames, final String applicationUri) throws Exception {
        try {
            return load(file, "PKCS12", SELF_SIGNED_PASSWORD, "default", SELF_SIGNED_PASSWORD, "default");
        } catch (final Exception e) {
            logger.warn("Failed to load existing key/cert material, creating new one");
        }

        final var result = createSelfSigned(hostnames, applicationUri);
        store(result, file, "PKCS12", SELF_SIGNED_PASSWORD, SELF_SIGNED_PASSWORD, "default");

        // done

        return result;
    }

    public static void store(
            final KeyCertMaterial material,
            final Path target,
            final String type,
            final char[] storePassword,
            final char[] keyPassword,
            final String alias
    ) throws Exception {

        final var keyStore = KeyStore.getInstance(type);

        keyStore.load(null);

        keyStore.setKeyEntry(alias, material.getServerKeyPair().getPrivate(), keyPassword, material.getServerCertificateChain());

        try (final OutputStream output = Files.newOutputStream(target)) {
            keyStore.store(output, storePassword);
        }

    }

    /**
     * Load key material from a keystore.
     *
     * @param source The file to load from
     * @param type The type to load (e.g. "PKCS12")
     * @param storePassword The password of the store.
     * @param keyAlias The alias of the key entry.
     * @param keyPassword The password of the key
     * @param certificateAlias The alias of the certificate entry.
     * @return The loaded key material.
     * @throws Exception If anything goes wrong.
     */
    public static KeyCertMaterial load(
            final Path source,
            final String type,
            final char[] storePassword,
            final String keyAlias,
            final char[] keyPassword,
            final String certificateAlias
    ) throws Exception {

        final var keyStore = KeyStore.getInstance(type);

        try (final InputStream input = Files.newInputStream(source)) {
            keyStore.load(input, storePassword);
        }

        final var privateKey = keyStore.getKey(keyAlias, keyPassword);
        if (!(privateKey instanceof PrivateKey)) {
            throw new Exception(String.format("Unable to find private key for alias: %s", keyAlias));
        }

        final var certificate = keyStore.getCertificate(certificateAlias);
        if (certificate == null) {
            throw new Exception(String.format("Unable to find certificate for alias: %s", certificateAlias));
        }
        final var keyPair = new KeyPair(certificate.getPublicKey(), (PrivateKey) privateKey);

        final var chain = keyStore.getCertificateChain(certificateAlias);
        if (chain == null) {
            throw new Exception(String.format("Unable to find a certificate chain for alias: %s", certificateAlias));
        }
        final var certificateChain = new ArrayList<X509Certificate>();
        for (final var cert : chain) {
            if (!(cert instanceof X509Certificate)) {
                throw new Exception(String.format("Certificate chain contained a non-X509 certificate: %s", cert));
            }
            certificateChain.add((X509Certificate) cert);
        }
        return new KeyCertMaterial(new Material(keyPair, certificateChain.toArray(X509Certificate[]::new)));

    }
}
