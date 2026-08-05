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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class PullTest {

    @Inject
    RunContextFactory runContextFactory;

    private Pull pullTask(WireMockRuntimeInfo wm) {
        return Pull.builder()
            .id("pull-test")
            .type(Pull.class.getName())
            .host(Property.ofValue(wm.getHttpBaseUrl()))
            .model(Property.ofValue("ai/smollm2"))
            .build();
    }

    @Test
    void happyPath_streamsAndSendsFromImage(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo("/models/create"))
            .willReturn(okJson("{\"status\":\"Pulling from ai/smollm2\"}")));

        var task = pullTask(wm);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThat(task.run(runContext), nullValue());
        verify(postRequestedFor(urlEqualTo("/models/create"))
            .withRequestBody(equalToJson("{\"fromImage\":\"ai/smollm2\"}")));
    }

    @Test
    void emptyBodyDoesNotFail(WireMockRuntimeInfo wm) throws Exception {
        // DMR may answer 200 with no streamed body (e.g. model already present): must not NPE.
        stubFor(post(urlEqualTo("/models/create"))
            .willReturn(aResponse().withStatus(200)));

        var task = pullTask(wm);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThat(task.run(runContext), nullValue());
    }

    @Test
    void errorLineInStreamThrows(WireMockRuntimeInfo wm) {
        // A streamed line carrying an "error" field must fail the task, not be reported as success.
        stubFor(post(urlEqualTo("/models/create"))
            .willReturn(okJson("{\"error\":\"manifest unknown\"}")));

        var task = pullTask(wm);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var e = assertThrows(Exception.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("ai/smollm2"));
    }

    @Test
    void nonSuccessStatusThrows(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/models/create"))
            .willReturn(aResponse().withStatus(500)));

        var task = pullTask(wm);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
