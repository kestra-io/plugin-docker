package io.kestra.plugin.docker.model;

import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class DeleteTest {

    @Inject
    RunContextFactory runContextFactory;

    private Delete deleteTask(WireMockRuntimeInfo wm) {
        return Delete.builder()
            .id("delete-test")
            .type(Delete.class.getName())
            .host(Property.ofValue(wm.getHttpBaseUrl()))
            .model(Property.ofValue("ai/smollm2"))
            .build();
    }

    @Test
    void happyPath_buildsCorrectUrl(WireMockRuntimeInfo wm) throws Exception {
        stubFor(delete(urlEqualTo("/models/ai/smollm2"))
            .willReturn(aResponse().withStatus(200)));

        var task = deleteTask(wm);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        verify(deleteRequestedFor(urlEqualTo("/models/ai/smollm2")));
    }

    @Test
    void nonSuccessStatusThrows(WireMockRuntimeInfo wm) {
        stubFor(delete(urlEqualTo("/models/ai/smollm2"))
            .willReturn(aResponse().withStatus(404)));

        var task = deleteTask(wm);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
