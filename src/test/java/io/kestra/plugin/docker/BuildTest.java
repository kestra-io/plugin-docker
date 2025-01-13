package io.kestra.plugin.docker;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.property.Property;
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
            .platforms(Property.of(List.of("linux/amd64")))
            .buildArgs(Property.of(Map.of("APT_PACKAGES", "curl")))
            .labels(Property.of(Map.of("unit-test", "true")))
            .tags(Property.of(List.of("unit-test")))
            .dockerfile(Property.of("""
                FROM ubuntu
                ARG APT_PACKAGES=""

                RUN apt-get update && apt-get install -y --no-install-recommends ${APT_PACKAGES};
            """))
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
            .platforms(Property.of(List.of("linux/amd64")))
            .buildArgs(Property.of(Map.of("APT_PACKAGES", "curl")))
            .labels(Property.of(Map.of("unit-test", "true")))
            .tags(Property.of(List.of("unit-test")))
            .dockerfile(Property.of(path.getFileName().toString()))
            .build();

        Build.Output run = task.run(runContext);
        assertThat(run.getImageId(), notNullValue());
    }
}
