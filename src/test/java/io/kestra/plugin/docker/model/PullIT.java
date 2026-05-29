package io.kestra.plugin.docker.model;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
@DockerModelRunnerTest
class PullIT {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void pullModel() throws Exception {
        var task = Pull.builder()
            .id("pull-it")
            .type(Pull.class.getName())
            .model(Property.ofValue("ai/smollm2"))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output, nullValue());
    }
}
