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
import io.kestra.plugin.scripts.runner.docker.PullPolicy;
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
            .containerImage(Property.ofValue("ubuntu"))
            .commands(TestsUtils.propertyFromList(List.of(
                "/bin/sh", "-c",
                "echo", "here",
                "echo {{ workingDir }} > output.txt",
                "echo 'Hello World' > output.txt")))
            .outputFiles(Property.ofValue(List.of("output.txt")))
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
            .containerImage(Property.ofValue(
                useRegistry ? getPrivateImage() : getRegistry() + "/" + getPrivateImage()
            ))
            .credentials(Credentials.builder()
                .username(Property.ofValue(getUsername()))
                .password(Property.ofValue(getPassword()))
                .registry(Property.ofValue(getRegistry()))
                .build())
            .commands(Property.ofValue(List.of("echo", "here")))
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
            .containerImage(Property.ofValue(getPrivateImage()))
            .credentials(Credentials.builder()
                .username(Property.ofValue(getUsername()))
                .password(Property.ofValue("incorrectPassword"))
                .registry(Property.ofValue(getRegistry()))
                .build())
            .commands(Property.ofValue(List.of("echo", "here")))
            .pullPolicy(Property.ofValue(PullPolicy.ALWAYS))
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, run, ImmutableMap.of());


        RetryUtils.RetryFailed exception = assertThrows(RetryUtils.RetryFailed.class, () -> run.run(runContext));
        assertThat(exception.getCause().getMessage(), is("Status 500: {\"message\":\"unauthorized: authentication required\"}\n"));
    }
}
