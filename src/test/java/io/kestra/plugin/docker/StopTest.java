package io.kestra.plugin.docker;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Docker;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class StopTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void runAndStop() throws Exception {
        Run run = Run.builder()
            .id("run")
            .type(Run.class.getName())
            .containerImage("redis")
            .wait(false)
            .build();
        RunContext runRunContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());

        ScriptOutput runOutput = run.run(runRunContext);
        Docker.DockerTaskRunnerDetailResult detailResult = (Docker.DockerTaskRunnerDetailResult) runOutput.getTaskRunner();

        assertThat(runOutput, notNullValue());
        assertThat(runOutput.getExitCode(), is(0));

        Stop stop = Stop.builder()
            .id("run")
            .type(Stop.class.getName())
            .containerId(Property.of(detailResult.getContainerId()))
            .build();
        RunContext stopRunContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());

        stop.run(stopRunContext);

        // should fail
        Exception exception = assertThrows(Exception.class, () -> stop.run(stopRunContext));

        assertThat(exception, notNullValue());
    }
}
