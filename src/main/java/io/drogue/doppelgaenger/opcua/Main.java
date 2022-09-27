package io.drogue.doppelgaenger.opcua;

import java.security.Security;

import de.dentrassi.crypto.pem.PemKeyStoreProvider;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main {
    public static void main(final String[] args) {
        Security.addProvider(new PemKeyStoreProvider());
        Quarkus.run(args);
    }
}
