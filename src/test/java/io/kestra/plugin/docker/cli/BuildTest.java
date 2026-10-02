package io.kestra.plugin.docker.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class BuildTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    ModelValidator modelValidator;

    @Test
    void inline() throws Exception {
        Build task = Build.builder()
            .id("unit-test")
            .type(Build.class.getName())
            .platforms(Property.ofValue(List.of("linux/amd64")))
            .buildArgs(Property.ofValue(Map.of("APT_PACKAGES", "curl")))
            .labels(Property.ofValue(Map.of("unit-test", "true")))
            .tags(Property.ofValue(List.of("unit-test")))
            .dockerfile(Property.ofValue("""
                    FROM ubuntu
                    ARG APT_PACKAGES=""

                    RUN apt-get update && apt-get install -y --no-install-recommends ${APT_PACKAGES};
                """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        Build.Output run = task.run(runContext);
        assertThat(run.getImageId(), notNullValue());
    }

    @ParameterizedTest
    @MethodSource("platformBuildArgs")
    void shouldAddPlatformBuildArgs(String platform, Map<String, String> expected) {
        Map<String, String> buildArgs = new HashMap<>();
        Build.addPlatformBuildArgs(buildArgs, platform);

        assertThat(buildArgs, is(expected));
    }

    static java.util.stream.Stream<Arguments> platformBuildArgs() {
        return Stream.of(
            Arguments.of("linux/amd64", Map.of(
                "TARGETPLATFORM", "linux/amd64",
                "TARGETOS", "linux",
                "TARGETARCH", "amd64"
            )),
            Arguments.of("linux/arm64/v8", Map.of(
                "TARGETPLATFORM", "linux/arm64/v8",
                "TARGETOS", "linux",
                "TARGETARCH", "arm64",
                "TARGETVARIANT", "v8"
            ))
        );
    }

    @Test
    void shouldPreserveExplicitPlatformBuildArgs() {
        Map<String, String> buildArgs = new HashMap<>(Map.of(
            "TARGETPLATFORM", "custom-platform",
            "TARGETOS", "custom-os",
            "TARGETARCH", "custom-arch",
            "TARGETVARIANT", "custom-variant"
        ));

        Build.addPlatformBuildArgs(buildArgs, "linux/arm64/v8");

        assertThat(buildArgs, is(Map.of(
            "TARGETPLATFORM", "custom-platform",
            "TARGETOS", "custom-os",
            "TARGETARCH", "custom-arch",
            "TARGETVARIANT", "custom-variant"
        )));
    }

    @Test
    void shouldExposeTargetPlatformArgsToDockerfile() throws Exception {
        Build task = Build.builder()
            .id("build-platform-args-" + IdUtils.create())
            .type(Build.class.getName())
            .platforms(Property.ofValue(List.of("linux/amd64")))
            .tags(Property.ofValue(List.of("unit-test-platform-args")))
            .dockerfile(Property.ofValue("""
                    FROM alpine
                    ARG TARGETOS
                    ARG TARGETARCH
                    RUN test "$TARGETOS" = linux && test "$TARGETARCH" = amd64
                """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        Build.Output run = task.run(runContext);
        assertThat(run.getImageId(), notNullValue());
    }

    @Test
    void shouldKeepExplicitTargetArgOverPlatform() throws Exception {
        Build task = Build.builder()
            .id("build-platform-args-" + IdUtils.create())
            .type(Build.class.getName())
            .platforms(Property.ofValue(List.of("linux/amd64")))
            .buildArgs(Property.ofValue(Map.of("TARGETARCH", "custom-arch")))
            .tags(Property.ofValue(List.of("unit-test-platform-args-override")))
            .dockerfile(Property.ofValue("""
                    FROM alpine
                    ARG TARGETARCH
                    RUN test "$TARGETARCH" = custom-arch
                """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        Build.Output run = task.run(runContext);
        assertThat(run.getImageId(), notNullValue());
    }

    @Test
    void target() throws Exception {
        Build task = Build.builder()
            .id("unit-test")
            .type(Build.class.getName())
            .tags(Property.ofValue(List.of("unit-test-target")))
            .target(Property.ofValue("base"))
            .dockerfile(Property.ofValue("""
                    FROM ubuntu AS base
                    RUN echo "base stage"

                    FROM base AS final
                    RUN exit 1
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
            .platforms(Property.ofValue(List.of("linux/amd64")))
            .buildArgs(Property.ofValue(Map.of("APT_PACKAGES", "curl")))
            .labels(Property.ofValue(Map.of("unit-test", "true")))
            .tags(Property.ofValue(List.of("unit-test")))
            .dockerfile(Property.ofValue(path.getFileName().toString()))
            .build();

        Build.Output run = task.run(runContext);
        assertThat(run.getImageId(), notNullValue());
    }

    @Test
    void shouldFailValidationWhenMissingDockerfile() {
        Build task = Build.builder()
            .id("unit-test")
            .type(Build.class.getName())
            .tags(Property.ofValue(List.of("unit-test")))
            .build();

        Optional<ConstraintViolationException> violations = modelValidator.isValid(task);
        assertThat(violations.isPresent(), is(true));
        assertThat(violations.get().getConstraintViolations().stream().anyMatch(v -> v.getPropertyPath().toString().equals("dockerfile")), is(true));
    }

    @Test
    void shouldFailAtRuntimeWhenEmptyTags() throws Exception {
        Build task = Build.builder()
            .id("unit-test")
            .type(Build.class.getName())
            .dockerfile(Property.ofValue("FROM ubuntu"))
            .tags(Property.ofValue(List.of()))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(e.getMessage(), is("At least one tag is required"));
    }

    @Test
    void shouldPassValidationWhenParsedFromFlowYaml() {
        Flow flow = YamlParser.parse("""
            id: build
            namespace: company.team
            tasks:
              - id: build
                type: io.kestra.plugin.docker.cli.Build
                dockerfile: FROM ubuntu
                tags:
                  - "{{ inputs.tag }}"
            """, Flow.class);

        assertThat(modelValidator.isValid(flow).isEmpty(), is(true));
    }

    @Test
    void shouldFailValidationWhenNullTags() {
        Build task = Build.builder()
            .id("unit-test")
            .type(Build.class.getName())
            .dockerfile(Property.ofValue("FROM ubuntu"))
            .build();

        Optional<ConstraintViolationException> violations = modelValidator.isValid(task);
        assertThat(violations.isPresent(), is(true));
        assertThat(violations.get().getConstraintViolations().stream().anyMatch(v -> v.getPropertyPath().toString().equals("tags")), is(true));
    }
}
