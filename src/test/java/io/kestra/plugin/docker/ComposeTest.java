package io.kestra.plugin.docker;

import com.github.dockerjava.api.DockerClient;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class ComposeTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;


    @Test
    void upCreatesContainer() throws Exception {
        var runContext = runContextFactory.of();

        var composeContent = """
            services:
              test_service:
                image: alpine:3.19
                command: ["sh", "-c", "sleep 30"]
            """;

        var upTask = Compose.builder()
            .id("compose-up")
            .type(Compose.class.getName())
            .composeFile(Property.ofValue(composeContent))
            .composeArgs(Property.ofValue(List.of(
                "-p", "kestra_compose_test",
                "up",
                "-d"
            )))
            .build();

        upTask.run(runContext);

        boolean containerFound;
        try (DockerClient client = new AbstractDockerHelper().getDockerClient(runContext, null, null, null)) {
            var containers = client.listContainersCmd()
                .withShowAll(true)
                .exec();

            containerFound = containers.stream().anyMatch(c -> {
                if (c.getLabels() == null) return false;
                return "test_service".equals(c.getLabels().get("com.docker.compose.service")) &&
                    "kestra_compose_test".equals(c.getLabels().get("com.docker.compose.project"));
            });
        }

        assertThat(containerFound, is(true));

        var downTask = Compose.builder()
            .id("compose-down")
            .type(Compose.class.getName())
            .composeFile(Property.ofValue(composeContent))
            .composeArgs(Property.ofValue(List.of(
                "-p", "kestra_compose_test",
                "down"
            )))
            .build();

        downTask.run(runContext);
    }
}
