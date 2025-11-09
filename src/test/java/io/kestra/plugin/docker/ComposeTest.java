package io.kestra.plugin.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class ComposeTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;

    private final AbstractDockerHelper helper = new AbstractDockerHelper();

    private boolean isDockerComposeAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "compose", "version")
                .redirectErrorStream(true)
                .start();
            int exit = process.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void upCreatesContainer() throws Exception {
        Assumptions.assumeTrue(isDockerComposeAvailable(), "docker compose not available, skipping ComposeTest");

        String composeContent = """
        services:
          test_service:
            image: alpine:3.19
            command: ["sh", "-c", "sleep 30"]
        """;

        Compose upTask = Compose.builder()
            .id("compose-up")
            .type(Compose.class.getName())
            .composeFile(Property.ofValue(composeContent))
            .command(Property.ofValue(Compose.Command.UP))
            .detach(Property.ofValue(true))
            .projectName(Property.ofValue("kestra_compose_test"))
            .build();

        RunContext upContext = TestsUtils.mockRunContext(runContextFactory, upTask, ImmutableMap.of());
        upTask.run(upContext);

        boolean found;
        try (DockerClient client = helper.getDockerClient(upContext, null, null, null)) {
            List<Container> containers = client.listContainersCmd()
                .withShowAll(true)
                .exec();

            found = containers.stream().anyMatch(c -> {
                if (c.getLabels() == null) {
                    return false;
                }
                String service = c.getLabels().get("com.docker.compose.service");
                String project = c.getLabels().get("com.docker.compose.project");
                return "test_service".equals(service) && "kestra_compose_test".equals(project);
            });
        }

        assertThat("Expected a container created by docker compose", found, is(true));

        Compose downTask = Compose.builder()
            .id("compose-down")
            .type(Compose.class.getName())
            .composeFile(Property.ofValue(composeContent))          // same inline YAML
            .command(Property.ofValue(Compose.Command.DOWN))
            .projectName(Property.ofValue("kestra_compose_test"))
            .build();

        RunContext downContext = TestsUtils.mockRunContext(runContextFactory, downTask, ImmutableMap.of());
        downTask.run(downContext);
    }

}
