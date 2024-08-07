package io.kestra.plugin.docker;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
class BuildTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void inline() throws Exception {
        Build task = Build.builder()
            .id("unit-test")
            .type(Build.class.getName())
            .platforms(List.of("linux/amd64"))
            .buildArgs(Map.of("APT_PACKAGES", "curl"))
            .labels(Map.of("unit-test", "true"))
            .tags(Set.of("unit-test"))
            .dockerfile("""
                FROM ubuntu
                ARG APT_PACKAGES=""
                
                RUN apt-get update && apt-get install -y --no-install-recommends ${APT_PACKAGES};
            """)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        Build.Output run = task.run(runContext);
        assertThat(run.getImageId(), notNullValue());
    }

    @Test
    void local() throws Exception {
        RunContext runContext = runContextFactory.of();

        Path path = runContext.workingDir().createTempFile(".DockerFile");
        Files.writeString(path, """
                FROM ubuntu
                ARG APT_PACKAGES=""
                
                RUN apt-get update && apt-get install -y --no-install-recommends ${APT_PACKAGES};
            """);

        Build task = Build.builder()
            .id("unit-test")
            .type(Build.class.getName())
            .platforms(List.of("linux/amd64"))
            .buildArgs(Map.of("APT_PACKAGES", "curl"))
            .labels(Map.of("unit-test", "true"))
            .tags(Set.of("unit-test"))
            .protocol(Build.Protocol.HTTP)
            .dockerfile(path.getFileName().toString())
            .build();

        Build.Output run = task.run(runContext);
        assertThat(run.getImageId(), notNullValue());
    }
}
