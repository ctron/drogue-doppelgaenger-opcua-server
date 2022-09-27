package io.drogue.doppelgaenger.opcua.milo;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Properties;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuildInfoLoader {

    private static final Logger logger = LoggerFactory.getLogger(BuildInfoLoader.class);

    private static final String DEFAULT_BUILD_NUMBER = "0";

    private BuildInfoLoader() {
    }

    public static void load(final BuildInfo.BuildInfoBuilder<?, ?> builder) {
        try {
            performLoad(builder);
        } catch (final Exception e) {
            logger.warn("Failed to load build info, using defaults", e);
            builder
                    .softwareVersion(OpcUaServer.SDK_VERSION)
                    .buildDate(DateTime.now())
                    .buildNumber(DEFAULT_BUILD_NUMBER);
            throw new RuntimeException(e);
        }
    }

    static void performLoad(final BuildInfo.BuildInfoBuilder<?, ?> builder) throws Exception {
        final Properties p = new Properties();
        try (final InputStream stream = BuildInfoLoader.class.getResourceAsStream("/git.properties")) {
            if (stream != null) {
                p.load(stream);
            }
        }

        final var version = Optional.ofNullable(p.getProperty("git.tags"))
                .flatMap(tags -> {
                    final var t = tags.split(",", 1);
                    if (t.length > 0) {
                        return Optional.ofNullable(t[0]);
                    } else {
                        return Optional.empty();
                    }
                })
                .filter(s -> !s.isEmpty())
                .or(() -> Optional.ofNullable(p.getProperty("git.build.version")))
                .orElse("0.0.0");

        builder
                .softwareVersion(version)
                .buildNumber(p.getProperty("git.commit.id.describe-short", DEFAULT_BUILD_NUMBER))
                .buildDate(Optional.ofNullable(p.getProperty("git.build.time"))
                        .map(OffsetDateTime::parse)
                        .map(OffsetDateTime::toInstant)
                        .map(DateTime::new)
                        .orElseGet(DateTime::now));
    }

}
