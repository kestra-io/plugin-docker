package io.kestra.plugin.docker;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Docker;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class StopTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void stopByContainerIdPrefix() throws Exception {
        final String image = "redis:6.2.17-alpine";
        String containerId = runContainer(runContextFactory, image);
        assertThat(containerExists(containerId, runContextFactory.of()), is(true));

        String prefix = containerId.substring(0, 12);

        Stop stop = Stop.builder()
            .id("stop")
            .type(Stop.class.getName())
            .containerId(Property.ofValue(prefix))
            .build();
        RunContext stopRunContext = TestsUtils.mockRunContext(runContextFactory, stop, ImmutableMap.of());

        stop.run(stopRunContext);

        assertThat(containerExists(containerId, runContextFactory.of()), is(false));
    }

    @Test
    void stopByContainerName() throws Exception {
        final String image = "redis:6.2.17-alpine";
        final String name = "kestra-stop-by-name-" + System.currentTimeMillis();

        String containerId = runNamedContainer(runContextFactory.of(), image, name);
        assertThat(containerExists(containerId, runContextFactory.of()), is(true));

        Stop stop = Stop.builder()
            .id("stop")
            .type(Stop.class.getName())
            .containerId(Property.ofValue(name))
            .build();
        RunContext stopRunContext = TestsUtils.mockRunContext(runContextFactory, stop, ImmutableMap.of());

        stop.run(stopRunContext);

        assertThat(containerExists(containerId, runContextFactory.of()), is(false));
    }

    @Test
    void runAndStop() throws Exception {
        Run run = Run.builder()
            .id("run")
            .type(Run.class.getName())
            .containerImage(Property.ofValue("redis"))
            .wait(Property.ofValue(false))
            .build();
        RunContext runRunContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());

        ScriptOutput runOutput = run.run(runRunContext);
        Docker.DockerTaskRunnerDetailResult detailResult = (Docker.DockerTaskRunnerDetailResult) runOutput.getTaskRunner();

        assertThat(runOutput, notNullValue());
        assertThat(runOutput.getExitCode(), is(0));

        Stop stop = Stop.builder()
            .id("run")
            .type(Stop.class.getName())
            .containerId(Property.ofValue(detailResult.getContainerId()))
            .build();
        RunContext stopRunContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());

        stop.run(stopRunContext);

        // should fail
        Exception exception = assertThrows(Exception.class, () -> stop.run(stopRunContext));

        assertThat(exception, notNullValue());
    }
}
