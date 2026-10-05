package io.kestra.plugin.docker.cli;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

class PushResponseItemCallbackTest {
    @Test
    void shouldHintDaemonCertificateConfigWhenRegistryCertificateIsUntrusted() {
        String message = "Get \"https://192.168.122.98:5000/v2/\": tls: failed to verify certificate: x509: certificate signed by unknown authority";

        String hinted = PushResponseItemCallback.withRegistryHint(message);

        assertThat(
            hinted, allOf(
                startsWith(message),
                containsString("/etc/docker/certs.d/192.168.122.98:5000/ca.crt"),
                containsString("insecure-registries")
            )
        );
    }

    @Test
    void shouldHintInsecureRegistriesWhenRegistryServesPlainHttp() {
        String message = "Get \"https://registry.local:5000/v2/\": http: server gave HTTP response to HTTPS client";

        String hinted = PushResponseItemCallback.withRegistryHint(message);

        assertThat(
            hinted, allOf(
                startsWith(message),
                containsString("Registry 'registry.local:5000' serves plain HTTP"),
                containsString("insecure-registries")
            )
        );
    }

    @Test
    void shouldKeepMessageUnchangedForOtherPushErrors() {
        String message = "denied: requested access to the resource is denied";

        assertThat(PushResponseItemCallback.withRegistryHint(message), is(message));
    }
}
