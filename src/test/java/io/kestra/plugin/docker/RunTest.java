package io.kestra.plugin.docker;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;

@MicronautTest
class RunTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        Run run = Run.builder()
            .id("run")
            .type(Run.class.getName())
            .docker(DockerOptions.builder().image("docker/whalesay").build())
            .commands(List.of("cowsay", "hello"))
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());

        ScriptOutput output = run.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getExitCode(), is(0));
    }
}