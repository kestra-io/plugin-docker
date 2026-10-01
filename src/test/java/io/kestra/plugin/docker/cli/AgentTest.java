package io.kestra.plugin.docker.cli;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@EnabledOnOs({OS.LINUX, OS.MAC})
class AgentTest {
    @Inject
    RunContextFactory runContextFactory;

    @TempDir
    Path tempDir;

    @Test
    void runsInlineConfigAndPrompt() throws Exception {
        String config = """
            agents:
              root:
                model: test/model
                description: Test agent
                instruction: Respond with a test result.
            """;
        String prompt = "hello 'agent'\\nsecond line";

        var task = Agent.builder()
            .id("agent")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue(config))
            .prompt(Property.ofValue(prompt))
            .taskRunner(Process.instance())
            .env(Property.ofValue(testEnvironment()))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        installFakeAgent();

        var output = task.run(runContext);

        assertThat(output.getExitCode(), is(0));
        assertThat(Files.readString(captureConfigPath(), StandardCharsets.UTF_8), is(config));
        assertThat(Files.readString(capturePromptPath(), StandardCharsets.UTF_8), is(prompt));
    }

    @Test
    void runsConfigFromRelativePath() throws Exception {
        String config = """
            agents:
              root:
                model: test/model
                description: Relative path agent
                instruction: Respond with a test result.
            """;

        var task = Agent.builder()
            .id("agent")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue("agent's.yaml"))
            .prompt(Property.ofValue("relative prompt"))
            .taskRunner(Process.instance())
            .env(Property.ofValue(testEnvironment()))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Files.writeString(runContext.workingDir().path().resolve("agent's.yaml"), config);
        installFakeAgent();

        var output = task.run(runContext);

        assertThat(output.getExitCode(), is(0));
        assertThat(Files.readString(captureConfigPath(), StandardCharsets.UTF_8), is(config));
        assertThat(Files.readString(capturePromptPath(), StandardCharsets.UTF_8), is("relative prompt"));
    }

    @Test
    void runsConfigFromInternalStorage() throws Exception {
        String config = """
            agents:
              root:
                model: test/model
                description: Stored agent
                instruction: Respond with a test result.
            """;

        var storageContext = runContextFactory.of();
        Path source = storageContext.workingDir().createTempFile(
            config.getBytes(StandardCharsets.UTF_8),
            ".yaml"
        );
        URI uri = storageContext.storage().putFile(source.toFile());

        var task = Agent.builder()
            .id("agent")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue(uri.toString()))
            .prompt(Property.ofValue("stored prompt"))
            .taskRunner(Process.instance())
            .env(Property.ofValue(testEnvironment()))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        installFakeAgent();

        var output = task.run(runContext);

        assertThat(output.getExitCode(), is(0));
        assertThat(Files.readString(captureConfigPath(), StandardCharsets.UTF_8), is(config));
        assertThat(Files.readString(capturePromptPath(), StandardCharsets.UTF_8), is("stored prompt"));
    }

    @Test
    void rejectsEmptyConfig() {
        var task = Agent.builder()
            .id("agent")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue("  "))
            .taskRunner(Process.instance())
            .env(Property.ofValue(testEnvironment()))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void failsWhenAgentProcessReturnsNonZero() throws Exception {
        var env = testEnvironment();
        env.put("FAKE_AGENT_FAIL", "true");

        var task = Agent.builder()
            .id("agent")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue("""
                agents:
                  root:
                    model: test/model
                    description: Test agent
                    instruction: Respond with a test result.
                """))
            .taskRunner(Process.instance())
            .env(Property.ofValue(env))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        installFakeAgent();

        assertThrows(RunnableTaskException.class, () -> task.run(runContext));
    }

    private Map<String, String> testEnvironment() {
        Map<String, String> env = new HashMap<>();
        String path = System.getenv("PATH");
        env.put("PATH", tempDir + java.io.File.pathSeparator + (path == null ? "" : path));
        env.put("FAKE_CONFIG_OUTPUT", captureConfigPath().toString());
        env.put("FAKE_PROMPT_OUTPUT", capturePromptPath().toString());
        return env;
    }

    private void installFakeAgent() throws Exception {
        Path executable = tempDir.resolve("docker-agent");
        Files.writeString(
            executable,
            """
            #!/bin/sh
            set -eu
            if [ "${FAKE_AGENT_FAIL:-false}" = "true" ]; then
              exit 7
            fi
            test "$1" = "run"
            test "$2" = "--exec"
            test -f "$3"
            cat "$3" > "$FAKE_CONFIG_OUTPUT"
            if [ "$#" -ge 4 ] && [ "$4" = "-" ]; then
              cat > "$FAKE_PROMPT_OUTPUT"
            fi
            printf 'agent result\\n'
            """
        );
        if (!executable.toFile().setExecutable(true)) {
            throw new IllegalStateException("Unable to make fake docker-agent executable");
        }
    }

    private Path captureConfigPath() {
        return tempDir.resolve("captured-config.yaml");
    }

    private Path capturePromptPath() {
        return tempDir.resolve("captured-prompt.txt");
    }
}
