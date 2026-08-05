package io.kestra.plugin.docker.model;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * Pulls the model itself first so the test is self-contained: it must not depend on PullIT
 * having run before it, since JUnit does not guarantee test-class execution order.
 */
@KestraTest
@DockerModelRunnerTest
class DeleteIT {

    private static final String MODEL = "ai/smollm2";

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void deleteModel() throws Exception {
        var pullTask = Pull.builder()
            .id("delete-it-pull-" + UUID.randomUUID())
            .type(Pull.class.getName())
            .model(Property.ofValue(MODEL))
            .build();
        pullTask.run(TestsUtils.mockRunContext(runContextFactory, pullTask, Map.of()));

        var deleteTask = Delete.builder()
            .id("delete-it-" + UUID.randomUUID())
            .type(Delete.class.getName())
            .model(Property.ofValue(MODEL))
            .build();
        deleteTask.run(TestsUtils.mockRunContext(runContextFactory, deleteTask, Map.of()));

        var listTask = ListModels.builder()
            .id("delete-it-list-" + UUID.randomUUID())
            .type(ListModels.class.getName())
            .build();
        var output = listTask.run(TestsUtils.mockRunContext(runContextFactory, listTask, Map.of()));

        assertThat(
            output.getModels().stream().flatMap(m -> m.tags() == null ? java.util.stream.Stream.empty() : m.tags().stream()).toList(),
            not(hasItem("docker.io/ai/smollm2:latest"))
        );
    }
}
