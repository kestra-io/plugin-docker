package io.kestra.plugin.docker.model;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

@KestraTest
@DockerModelRunnerTest
class ConfigureIT {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void configureModel() throws Exception {
        var task = Configure.builder()
            .id("configure-it")
            .type(Configure.class.getName())
            .model(Property.ofValue("ai/smollm2"))
            .contextSize(Property.ofValue(4096))
            .runtimeFlags(Property.ofValue(List.of("--temp 0.7")))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);
    }
}
