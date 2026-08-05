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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class ListTest {

    @Inject
    RunContextFactory runContextFactory;

    // Real DMR shape: a bare JSON array, "id" is a content digest, tags carry the human-readable name.
    private static final String REAL_MODELS_RESPONSE =
        "[{\"id\":\"sha256:653017dd060f5cd345118ff90382ceb213d383de2887820d2f303893d32ef40d\","
            + "\"tags\":[\"docker.io/ai/nomic-embed-text-v1.5:latest\"],\"created\":1778854020,"
            + "\"config\":{\"format\":\"gguf\",\"quantization\":\"MOSTLY_F16\",\"parameters\":\"136.73M\","
            + "\"architecture\":\"nomic-bert\",\"size\":\"260.86MiB\",\"gguf\":{\"general.architecture\":\"nomic-bert\"}}}]";

    private List task(String baseUrl) {
        return List.builder()
            .id("list-models-test-" + UUID.randomUUID())
            .type(List.class.getName())
            .host(Property.ofValue(baseUrl))
            .build();
    }

    @Test
    void happyPath(WireMockRuntimeInfo wm) throws Exception {
        stubFor(get(urlEqualTo("/models")).willReturn(okJson(REAL_MODELS_RESPONSE)));

        var task = task(wm.getHttpBaseUrl());
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output.getModels(), hasSize(1));
        var model = output.getModels().getFirst();
        assertThat(model.id(), is("sha256:653017dd060f5cd345118ff90382ceb213d383de2887820d2f303893d32ef40d"));
        assertThat(model.tags(), contains("docker.io/ai/nomic-embed-text-v1.5:latest"));
        assertThat(model.created(), is(1778854020L));
        assertThat(model.config().quantization(), is("MOSTLY_F16"));
        assertThat(model.config().architecture(), is("nomic-bert"));
    }

    @Test
    void nonTwoxx_includesResponseBodyInMessage(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/models")).willReturn(aResponse().withStatus(500).withBody("internal DMR error")));

        var task = task(wm.getHttpBaseUrl());
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var ex = assertThrows(Exception.class, () -> task.run(runContext));

        assertThat(ex.getMessage(), containsString("internal DMR error"));
    }

    @Test
    void emptyBody_throwsActionableError(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/models")).willReturn(aResponse().withStatus(200)));

        var task = task(wm.getHttpBaseUrl());
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));

        assertThat(ex.getMessage(), containsString("empty response"));
    }

    @Test
    void malformedBody_throwsActionableError(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/models")).willReturn(aResponse().withStatus(200).withBody("{\"models\": not-json")));

        var task = task(wm.getHttpBaseUrl());
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));

        assertThat(ex.getMessage(), containsString("Failed to parse"));
    }

    // A peer that accepts the connection then closes it without a response makes the transport raise
    // NoHttpResponseException; this proves AbstractModel#execute wraps it into an actionable error.
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

            var task = task("http://localhost:" + serverSocket.getLocalPort());
            var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
            var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));

            assertThat(ex.getMessage(), containsString("Failed to list models on Docker Model Runner"));
            assertThat(ex.getCause(), notNullValue());
        }
    }
}
