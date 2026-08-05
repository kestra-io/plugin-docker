package io.kestra.plugin.docker.model;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Relies on ai/nomic-embed-text-v1.5 being present on the live DMR instance. No other IT test
 * in this suite deletes or renames it.
 */
@KestraTest
@DockerModelRunnerTest
class ListIT {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void listModels() throws Exception {
        var task = List.builder()
            .id("list-models-it")
            .type(List.class.getName())
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output.getModels(), hasSize(greaterThan(0)));
        assertThat(
            output.getModels().stream().flatMap(m -> m.tags() == null ? java.util.stream.Stream.empty() : m.tags().stream()).toList(),
            hasItem("docker.io/ai/nomic-embed-text-v1.5:latest")
        );

        var nomicModel = output.getModels().stream()
            .filter(m -> m.tags() != null && m.tags().contains("docker.io/ai/nomic-embed-text-v1.5:latest"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ai/nomic-embed-text-v1.5 not found in DMR /models response"));

        assertThat(nomicModel.id(), startsWith("sha256:"));
        assertThat(nomicModel.created(), notNullValue());
        assertThat(nomicModel.config(), notNullValue());
        assertThat(nomicModel.config().architecture(), is("nomic-bert"));
    }
}
