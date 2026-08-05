package io.kestra.plugin.docker.model;

import java.util.List;
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
class ConfigureTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void happyPath_sendsContextSizeAndFlags(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo("/models/ai/smollm2/configure"))
            .willReturn(aResponse().withStatus(200)));

        var task = Configure.builder()
            .id("configure-test")
            .type(Configure.class.getName())
            .host(Property.ofValue(wm.getHttpBaseUrl()))
            .model(Property.ofValue("ai/smollm2"))
            .contextSize(Property.ofValue(4096))
            .runtimeFlags(Property.ofValue(List.of("--temp 0.7")))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        verify(postRequestedFor(urlEqualTo("/models/ai/smollm2/configure"))
            .withRequestBody(equalToJson("{\"contextSize\":4096,\"runtimeFlags\":[\"--temp 0.7\"]}")));
    }

    @Test
    void skipsCallWhenNothingSet(WireMockRuntimeInfo wm) throws Exception {
        var task = Configure.builder()
            .id("configure-noop-test")
            .type(Configure.class.getName())
            .host(Property.ofValue(wm.getHttpBaseUrl()))
            .model(Property.ofValue("ai/smollm2"))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        // Neither contextSize nor runtimeFlags set: no HTTP call must be made.
        verify(exactly(0), anyRequestedFor(anyUrl()));
    }

    @Test
    void nonSuccessStatusThrows(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/models/ai/smollm2/configure"))
            .willReturn(aResponse().withStatus(400)));

        var task = Configure.builder()
            .id("configure-error-test")
            .type(Configure.class.getName())
            .host(Property.ofValue(wm.getHttpBaseUrl()))
            .model(Property.ofValue("ai/smollm2"))
            .contextSize(Property.ofValue(4096))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
