package io.kestra.plugin.docker.model;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class PullTest {

    @Inject
    RunContextFactory runContextFactory;

    private WireMockServer server;

    @BeforeEach
    void startStub() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop();
    }

    private Pull pullTask() {
        return Pull.builder()
            .id("pull-test")
            .type(Pull.class.getName())
            .host(Property.ofValue("http://localhost:" + server.port()))
            .model(Property.ofValue("ai/smollm2"))
            .build();
    }

    @Test
    void happyPath_streamsAndSendsFromImage() throws Exception {
        server.stubFor(post(urlEqualTo("/models/create"))
            .willReturn(okJson("{\"status\":\"Pulling from ai/smollm2\"}")));

        var task = pullTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThat(task.run(runContext), nullValue());
        server.verify(postRequestedFor(urlEqualTo("/models/create"))
            .withRequestBody(equalToJson("{\"fromImage\":\"ai/smollm2\"}")));
    }

    @Test
    void emptyBodyDoesNotFail() throws Exception {
        // DMR may answer 200 with no streamed body (e.g. model already present): must not NPE.
        server.stubFor(post(urlEqualTo("/models/create"))
            .willReturn(aResponse().withStatus(200)));

        var task = pullTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThat(task.run(runContext), nullValue());
    }

    @Test
    void errorLineInStreamThrows() {
        // A streamed line carrying an "error" field must fail the task, not be reported as success.
        server.stubFor(post(urlEqualTo("/models/create"))
            .willReturn(okJson("{\"error\":\"manifest unknown\"}")));

        var task = pullTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var e = assertThrows(Exception.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("ai/smollm2"));
    }

    @Test
    void nonSuccessStatusThrows() {
        server.stubFor(post(urlEqualTo("/models/create"))
            .willReturn(aResponse().withStatus(500)));

        var task = pullTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
