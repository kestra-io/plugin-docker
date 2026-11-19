package io.kestra.plugin.docker.cli;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.DockerClient;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.runner.docker.Docker;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ComposeTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void successfulBeforeCommandsKeepComposeArguments() throws Exception {
        var task = Compose.builder()
            .id("compose-before-commands-ok")
            .type(Compose.class.getName())
            .beforeCommands(Property.ofValue(List.of("echo before")))
            .taskRunner(Docker.builder().type(Docker.instance().getType()).build())
            .composeArgs(Property.ofValue(List.of("version", "--short")))
            .build();

        var output = task.run(TestsUtils.mockRunContext(runContextFactory, task, Map.of()));

        assertThat(output.getExitCode(), is(0));
    }

    @Test
    void beforeCommandsAreExecuted() {
        var task = Compose.builder()
            .id("compose-before-commands")
            .type(Compose.class.getName())
            .beforeCommands(Property.ofValue(List.of("exit 7")))
            .taskRunner(Docker.builder().type(Docker.instance().getType()).build())
            .composeArgs(Property.ofValue(List.of("version")))
            .build();

        var exception = assertThrows(RunnableTaskException.class, () ->
        {
            var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
            task.run(runContext);
        });

        assertThat(((TaskException) exception.getCause()).getExitCode(), is(7));
    }

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
            .taskRunner(
                Docker.builder()
                    .type(Docker.instance().getType())
                    .volumes(List.of("/var/run/docker.sock:/var/run/docker.sock"))
                    .build()
            )
            .composeArgs(
                Property.ofValue(
                    List.of(
                        "-p", "kestra_compose_test",
                        "up",
                        "-d"
                    )
                )
            )
            .build();

        runContext = TestsUtils.mockRunContext(runContextFactory, upTask, Map.of());
        upTask.run(runContext);

        boolean containerFound;
        try (DockerClient client = new AbstractDockerHelper().getDockerClient(runContext, null, null, null)) {
            var containers = client.listContainersCmd()
                .withShowAll(true)
                .exec();

            containerFound = containers.stream().anyMatch(c ->
            {
                if (c.getLabels() == null)
                    return false;
                return "test_service".equals(c.getLabels().get("com.docker.compose.service")) &&
                    "kestra_compose_test".equals(c.getLabels().get("com.docker.compose.project"));
            });
        }

        assertThat(containerFound, is(true));

        var downTask = Compose.builder()
            .id("compose-down")
            .type(Compose.class.getName())
            .composeFile(Property.ofValue(composeContent))
            .taskRunner(
                Docker.builder()
                    .type(Docker.instance().getType())
                    .volumes(List.of("/var/run/docker.sock:/var/run/docker.sock"))
                    .build()
            )
            .composeArgs(
                Property.ofValue(
                    List.of(
                        "-p", "kestra_compose_test",
                        "down"
                    )
                )
            )
            .build();

        runContext = TestsUtils.mockRunContext(runContextFactory, upTask, Map.of());
        downTask.run(runContext);
    }
}
