package io.kestra.plugin.docker;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.RetryUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.scripts.runner.docker.Credentials;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class RunTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        Run run = Run.builder()
            .id("run")
            .type(Run.class.getName())
            .containerImage(Property.of("ubuntu"))
            .commands(List.of(
                "/bin/sh", "-c",
                "echo", "here",
                "echo {{ workingDir }} > output.txt",
                "echo 'Hello World' > output.txt"))
            .outputFiles(Property.of(List.of("output.txt")))
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());

        ScriptOutput output = run.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getExitCode(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void run_pullImageFromPrivateRepo_correctCredentials(boolean useRegistry) throws Exception {
        Run run = Run.builder()
            .id(Run.class.getSimpleName())
            .type(Run.class.getName())
            .containerImage(Property.of(
                useRegistry ? getPrivateImage() : getRegistry() + "/" + getPrivateImage()
            ))
            .credentials(Credentials.builder()
                .username(Property.of(getUsername()))
                .password(Property.of(getPassword()))
                .registry(Property.of(getRegistry()))
                .build())
            .commands(List.of("echo", "here"))
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());

        ScriptOutput output = run.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getExitCode(), is(0));
    }

    @Test
    void run_pullImageFromPrivateRepo_incorrectCredentials() {
        Run run = Run.builder()
            .id(Run.class.getSimpleName())
            .type(Run.class.getName())
            .containerImage(Property.of(getPrivateImage()))
            .credentials(Credentials.builder()
                .username(Property.of(getUsername()))
                .password(Property.of("incorrectPassword"))
                .registry(Property.of(getRegistry()))
                .build())
            .commands(List.of("echo", "here"))
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());


        RetryUtils.RetryFailed exception = assertThrows(RetryUtils.RetryFailed.class, () -> run.run(runContext));
        assertThat(exception.getCause().getMessage(), is("Status 500: {\"message\":\"unauthorized: authentication required\"}\n"));
    }
}
