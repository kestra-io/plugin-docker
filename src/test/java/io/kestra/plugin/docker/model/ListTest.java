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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ListTest {

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

    private List listTask() {
        return List.builder()
            .id("list-test")
            .type(List.class.getName())
            .host(Property.ofValue("http://localhost:" + server.port()))
            .build();
    }

    @Test
    void happyPath() throws Exception {
        server.stubFor(get(urlEqualTo("/models"))
            .willReturn(okJson("{\"models\":[{\"id\":\"ai/smollm2\",\"created\":1234567890,\"owned_by\":\"docker\"}]}")));

        var task = listTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output.getModels(), hasSize(1));
        assertThat(output.getModels().getFirst().id(), is("ai/smollm2"));
        assertThat(output.getModels().getFirst().created(), is(1234567890L));
        assertThat(output.getModels().getFirst().ownedBy(), is("docker"));
    }

    @Test
    void missingModelsFieldReturnsEmptyList() throws Exception {
        // A JSON body with no "models" field must yield an empty list, not an NPE.
        server.stubFor(get(urlEqualTo("/models"))
            .willReturn(okJson("{}")));

        var task = listTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output.getModels(), is(empty()));
    }

    @Test
    void nonSuccessStatusThrows() {
        server.stubFor(get(urlEqualTo("/models"))
            .willReturn(aResponse().withStatus(500)));

        var task = listTask();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
