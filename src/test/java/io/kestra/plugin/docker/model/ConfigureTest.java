package io.kestra.plugin.docker.model;

import java.util.List;
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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ConfigureTest {

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

    @Test
    void happyPath_sendsContextSizeAndFlags() throws Exception {
        server.stubFor(post(urlEqualTo("/models/ai/smollm2/configure"))
            .willReturn(aResponse().withStatus(200)));

        var task = Configure.builder()
            .id("configure-test")
            .type(Configure.class.getName())
            .host(Property.ofValue("http://localhost:" + server.port()))
            .model(Property.ofValue("ai/smollm2"))
            .contextSize(Property.ofValue(4096))
            .runtimeFlags(Property.ofValue(List.of("--temp 0.7")))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        server.verify(postRequestedFor(urlEqualTo("/models/ai/smollm2/configure"))
            .withRequestBody(equalToJson("{\"contextSize\":4096,\"runtimeFlags\":[\"--temp 0.7\"]}")));
    }

    @Test
    void skipsCallWhenNothingSet() throws Exception {
        var task = Configure.builder()
            .id("configure-noop-test")
            .type(Configure.class.getName())
            .host(Property.ofValue("http://localhost:" + server.port()))
            .model(Property.ofValue("ai/smollm2"))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        // Neither contextSize nor runtimeFlags set: no HTTP call must be made.
        assertThat(server.getAllServeEvents(), empty());
    }

    @Test
    void nonSuccessStatusThrows() {
        server.stubFor(post(urlEqualTo("/models/ai/smollm2/configure"))
            .willReturn(aResponse().withStatus(400)));

        var task = Configure.builder()
            .id("configure-error-test")
            .type(Configure.class.getName())
            .host(Property.ofValue("http://localhost:" + server.port()))
            .model(Property.ofValue("ai/smollm2"))
            .contextSize(Property.ofValue(4096))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
