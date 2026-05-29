package io.kestra.plugin.docker.model;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

@KestraTest
@DockerModelRunnerTest
class DeleteIT {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void deleteModel() throws Exception {
        var task = Delete.builder()
            .id("delete-it")
            .type(Delete.class.getName())
            .model(Property.ofValue("ai/smollm2"))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);
    }
}
