package io.kestra.plugin.docker.cli;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.PushResponseItem;

import io.kestra.core.runners.RunContext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;

class PushResponseItemCallbackTest {
    private static final String UNKNOWN_AUTHORITY = "Get \"https://192.168.122.98:5000/v2/\": tls: failed to verify certificate: x509: certificate signed by unknown authority";

    @Test
    void shouldHintDaemonCertificateConfigWhenRegistryCertificateIsUntrusted() {
        var hinted = PushResponseItemCallback.withRegistryHint(UNKNOWN_AUTHORITY);

        assertThat(
            hinted, allOf(
                startsWith(UNKNOWN_AUTHORITY),
                containsString("/etc/docker/certs.d/192.168.122.98:5000/ca.crt"),
                containsString("insecure-registries")
            )
        );
    }

    @Test
    void shouldHintInsecureRegistriesWhenRegistryServesPlainHttp() {
        var message = "Get \"https://registry.local:5000/v2/\": http: server gave HTTP response to HTTPS client";

        var hinted = PushResponseItemCallback.withRegistryHint(message);

        assertThat(
            hinted, allOf(
                startsWith(message),
                containsString("Registry 'registry.local:5000' serves plain HTTP"),
                containsString("insecure-registries")
            )
        );
    }

    @Test
    void shouldUsePlaceholderHostWhenErrorHasNoRegistryUrl() {
        var hinted = PushResponseItemCallback.withRegistryHint("tls: failed to verify certificate: x509: certificate signed by unknown authority");

        assertThat(hinted, containsString("/etc/docker/certs.d/<registry-host>/ca.crt"));
    }

    @Test
    void shouldKeepMessageUnchangedForOtherCertificateErrors() {
        var message = "Get \"https://registry.local:5000/v2/\": tls: failed to verify certificate: x509: certificate has expired or is not yet valid";

        assertThat(PushResponseItemCallback.withRegistryHint(message), is(message));
    }

    @Test
    void shouldKeepMessageUnchangedForOtherPushErrors() {
        var message = "denied: requested access to the resource is denied";

        assertThat(PushResponseItemCallback.withRegistryHint(message), is(message));
    }

    @Test
    void shouldReturnNullWhenMessageIsNull() {
        assertThat(PushResponseItemCallback.withRegistryHint(null), nullValue());
    }

    @Test
    void shouldExposeHintedErrorWhenPushStreamReportsUntrustedCertificate() {
        var item = new ObjectMapper().convertValue(Map.of("errorDetail", Map.of("message", UNKNOWN_AUTHORITY)), PushResponseItem.class);
        var callback = new PushResponseItemCallback(mock(RunContext.class));

        callback.onNext(item);

        assertThat(callback.getError().getMessage(), allOf(startsWith(UNKNOWN_AUTHORITY), containsString("/etc/docker/certs.d/192.168.122.98:5000/ca.crt")));
    }

    @Test
    void shouldExposeHintedErrorAndKeepCauseWhenTransportFails() {
        var failure = new IllegalStateException(UNKNOWN_AUTHORITY);
        var callback = new PushResponseItemCallback(mock(RunContext.class));

        callback.onError(failure);

        assertThat(callback.getError().getMessage(), containsString("/etc/docker/certs.d/192.168.122.98:5000/ca.crt"));
        assertThat(callback.getError().getCause(), sameInstance(failure));
    }
}
