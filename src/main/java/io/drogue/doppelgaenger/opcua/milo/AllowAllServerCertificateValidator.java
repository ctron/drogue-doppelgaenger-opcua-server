package io.drogue.doppelgaenger.opcua.milo;

import static org.eclipse.milo.opcua.stack.core.util.validation.CertificateValidationUtil.checkApplicationUri;
import static org.eclipse.milo.opcua.stack.core.util.validation.CertificateValidationUtil.checkEndEntityExtendedKeyUsage;
import static org.eclipse.milo.opcua.stack.core.util.validation.CertificateValidationUtil.checkEndEntityKeyUsage;
import static org.eclipse.milo.opcua.stack.core.util.validation.CertificateValidationUtil.checkValidity;

import java.security.cert.X509Certificate;
import java.util.List;

import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.server.security.ServerCertificateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllowAllServerCertificateValidator implements ServerCertificateValidator {

    private static final Logger logger = LoggerFactory.getLogger(AllowAllServerCertificateValidator.class);

    @Override
    public void validateCertificateChain(final List<X509Certificate> certificateChain, final String applicationUri) throws UaException {

        validateCertificateChain(certificateChain);

        final X509Certificate certificate = certificateChain.get(0);
        checkApplicationUri(certificate, applicationUri);

    }

    @Override
    public void validateCertificateChain(final List<X509Certificate> certificateChain) throws UaException {

        final X509Certificate certificate = certificateChain.get(0);

        checkValidity(certificate, true);
        checkEndEntityKeyUsage(certificate);
        checkEndEntityExtendedKeyUsage(certificate, true);

    }
}
