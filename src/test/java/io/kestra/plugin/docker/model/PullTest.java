package io.kestra.plugin.docker.model;

import java.net.ServerSocket;
import java.util.Map;
import java.util.UUID;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class PullTest {

    @Inject
    RunContextFactory runContextFactory;

    private Pull task(String baseUrl, String model) {
        return Pull.builder()
            .id("pull-test-" + UUID.randomUUID())
            .type(Pull.class.getName())
            .host(Property.ofValue(baseUrl))
            .model(Property.ofValue(model))
            .build();
    }

    @Test
    void happyPath_sendsFromFieldAndStreamsProgress(WireMockRuntimeInfo wm) throws Exception {
        // Real DMR streaming shape captured against a live instance.
        stubFor(post(urlEqualTo("/models/create")).willReturn(aResponse().withStatus(200).withBody(
            "{\"type\":\"progress\",\"message\":\"Downloaded: 0.01 MB\",\"total\":274303184,\"layer\":{\"id\":\"sha256:abc\",\"size\":12624,\"current\":12624},\"mode\":\"pull\"}\n"
                + "{\"type\":\"success\",\"message\":\"Model pulled successfully\"}\n"
        )));

        var task = task(wm.getHttpBaseUrl(), "ai/smollm2");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThat(task.run(runContext), nullValue());
        // Verified against a real DMR: the correct request field is "from", not "fromImage".
        verify(postRequestedFor(urlEqualTo("/models/create"))
            .withRequestBody(equalToJson("{\"from\":\"ai/smollm2\"}")));
    }

    @Test
    void errorLine_throwsWithDmrMessage(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/models/create")).willReturn(aResponse().withStatus(200).withBody(
            "{\"type\":\"error\",\"error\":\"Invalid model reference\"}\n"
        )));

        var task = task(wm.getHttpBaseUrl(), "not-a-model");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("Invalid model reference"));
    }

    @Test
    void nonTwoxx_includesResponseBodyInMessage(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/models/create")).willReturn(aResponse().withStatus(400).withBody("Invalid model reference")));

        var task = task(wm.getHttpBaseUrl(), "ai/smollm2");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var ex = assertThrows(Exception.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("Invalid model reference"));
    }

    // A peer that accepts the connection then closes it without a response makes the transport raise
    // NoHttpResponseException; this proves AbstractModel#executeStreaming wraps it into an actionable error.
    @Test
    void transportFailure_throwsActionableError() throws Exception {
        try (var serverSocket = new ServerSocket(0)) {
            var acceptThread = new Thread(() -> {
                try {
                    while (!serverSocket.isClosed()) {
                        serverSocket.accept().close();
                    }
                } catch (java.io.IOException ignored) {
                    // server socket closed, stop accepting
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            var task = task("http://localhost:" + serverSocket.getLocalPort(), "ai/smollm2");
            var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
            var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));

            assertThat(ex.getMessage(), containsString("Failed to pull model 'ai/smollm2' on Docker Model Runner"));
            assertThat(ex.getCause(), notNullValue());
        }
    }
}
