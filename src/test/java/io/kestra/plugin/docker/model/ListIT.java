package io.kestra.plugin.docker.model;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@DockerModelRunnerTest
class ListIT {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void listModels() throws Exception {
        var task = List.builder()
            .id("list-it")
            .type(List.class.getName())
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output.getModels(), notNullValue());
        assertThat(output.getModels(), hasSize(greaterThanOrEqualTo(0)));
        output.getModels().forEach(m -> assertThat(m.id(), notNullValue()));
    }
}
