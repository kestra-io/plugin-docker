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
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DeleteTest {

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

    private Delete deleteTask() {
        return Delete.builder()
            .id("delete-test")
            .type(Delete.class.getName())
            .host(Property.ofValue("http://localhost:" + server.port()))
            .model(Property.ofValue("ai/smollm2"))
            .build();
    }

    @Test
    void happyPath_buildsCorrectUrl() throws Exception {
        server.stubFor(delete(urlEqualTo("/models/ai/smollm2"))
            .willReturn(aResponse().withStatus(200)));

        var task = deleteTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        server.verify(deleteRequestedFor(urlEqualTo("/models/ai/smollm2")));
    }

    @Test
    void nonSuccessStatusThrows() {
        server.stubFor(delete(urlEqualTo("/models/ai/smollm2"))
            .willReturn(aResponse().withStatus(404)));

        var task = deleteTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
