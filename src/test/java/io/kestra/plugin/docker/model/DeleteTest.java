package io.kestra.plugin.docker.model;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class DeleteTest {

    @Inject
    RunContextFactory runContextFactory;

    private Delete task(String baseUrl, String model) {
        return Delete.builder()
            .id("delete-test-" + UUID.randomUUID())
            .type(Delete.class.getName())
            .host(Property.ofValue(baseUrl))
            .model(Property.ofValue(model))
            .build();
    }

    @Test
    void happyPath_buildsCorrectUrl(WireMockRuntimeInfo wm) throws Exception {
        // Real DMR shape: array of untag/delete actions.
        stubFor(delete(urlEqualTo("/models/ai/smollm2"))
            .willReturn(okJson("[{\"Untagged\":\"docker.io/ai/smollm2:latest\"},{\"Deleted\":\"sha256:abc\"}]")));

        var task = task(wm.getHttpBaseUrl(), "ai/smollm2");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        verify(deleteRequestedFor(urlEqualTo("/models/ai/smollm2")));
    }

    @Test
    void nonTwoxx_includesResponseBodyInMessage(WireMockRuntimeInfo wm) {
        stubFor(delete(urlEqualTo("/models/ai/does-not-exist"))
            .willReturn(aResponse().withStatus(404).withBody("error while deleting model: model not found")));

        var task = task(wm.getHttpBaseUrl(), "ai/does-not-exist");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var ex = assertThrows(Exception.class, () -> task.run(runContext));

        assertThat(ex.getMessage(), containsString("error while deleting model: model not found"));
    }
}
