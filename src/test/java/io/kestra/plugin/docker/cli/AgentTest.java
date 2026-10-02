package io.kestra.plugin.docker.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Docker;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.ConstraintViolationException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
@WireMockTest
class AgentTest {
    private static final String CONFIG = """
        agents:
          root:
            model: openai/gpt-5
            instruction: Review release notes.
        """;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> logQueue;

    private Agent task(String config) {
        Property<String> agentConfig = null;
        if (config != null) {
            agentConfig = config.contains("{{") ? Property.ofExpression(config) : Property.ofValue(config);
        }
        return Agent.builder()
            .id("agent-test")
            .type(Agent.class.getName())
            .agentConfig(agentConfig)
            .prompt(Property.ofValue("Say hello."))
            .build();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            CONFIG,
            "agents: {root: {model: openai/gpt-5}}",
            "{agents: {root: {model: openai/gpt-5}}}"
        }
    )
    void inlineYamlIsWrittenToTemporaryFile(String config) throws Exception {
        var task = task(config);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        Path resolved = task.resolveAgentConfig(runContext);

        assertThat(Files.readString(resolved), is(config));
        assertThat(resolved.getFileName().toString().endsWith(".yaml"), is(true));
        assertThat(resolved.getParent(), is(runContext.workingDir().path()));
    }

    @Test
    void relativeFileIsPassedDirectlyWithoutChangingSource() throws Exception {
        var task = task("configs/agent.yaml");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Path source = runContext.workingDir().resolve(Path.of("configs/agent.yaml"));
        Files.createDirectories(source.getParent());
        Files.writeString(source, CONFIG);

        Path resolved = task.resolveAgentConfig(runContext);

        assertThat(resolved, is(source));
        assertThat(resolved.getParent(), is(source.getParent()));
        assertThat(Files.readString(source), is(CONFIG));
    }

    @Test
    void storageFileInputIsRenderedAndDownloadedWithoutChangingSource() throws Exception {
        var task = task("{{ inputs.config }}");
        var storageContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var uri = storageContext.storage().putFile(
            new ByteArrayInputStream(CONFIG.getBytes(StandardCharsets.UTF_8)), "agent.yaml"
        );
        try {
            var runContext = TestsUtils.mockRunContext(
                runContextFactory, task, Map.of("config", uri.toString())
            );

            Path resolved = task.resolveAgentConfig(runContext);

            assertThat(Files.readString(resolved), is(CONFIG));
            Files.writeString(resolved, "changed");
            try (var input = storageContext.storage().getFile(uri)) {
                assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8), is(CONFIG));
            }
        } finally {
            storageContext.storage().deleteFile(uri);
        }
    }

    @Test
    void missingConfigurationIsRejected() {
        var task = task(null);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(ConstraintViolationException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), containsString("agentConfig"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\n\t" })
    void blankConfigurationIsRejected(String config) {
        var task = task(config);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), is("agentConfig must not be blank."));
    }

    @Test
    void missingRelativeFileIsRejected() {
        var task = task("missing.yaml");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), containsString("file does not exist or is not a regular file"));
    }

    @Test
    void directoryIsRejected() throws Exception {
        var task = task("configs");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Files.createDirectory(runContext.workingDir().resolve(Path.of("configs")));

        var exception = assertThrows(IllegalArgumentException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), containsString("file does not exist or is not a regular file"));
    }

    @Test
    void missingStorageFileFails() {
        var task = task("kestra:///missing-agent-config.yaml");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(IOException.class, () -> task.resolveAgentConfig(runContext));
    }

    @Test
    void absoluteFilePathIsRejected() {
        var task = task(Path.of("agent.yaml").toAbsolutePath().toString());
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), containsString("must be relative to the working directory"));
    }

    @Test
    void commandWithPromptUsesHeadlessModeAndConfigurationPath() throws Exception {
        var task = task(CONFIG);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Path configPath = task.resolveAgentConfig(runContext);

        assertThat(task.buildAgentCommand(runContext, configPath), is(List.of("/docker-agent", "run", "--exec", "--", configPath.toString(), "Say hello.")));
    }

    @Test
    void missingPromptIsRejected() throws Exception {
        var task = Agent.builder()
            .id("agent-missing-prompt-test")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue(CONFIG))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var exception = assertThrows(ConstraintViolationException.class, () -> task.buildAgentCommand(runContext, Path.of("agent.yaml")));

        assertThat(exception.getMessage(), containsString("prompt"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\n\t" })
    void blankRenderedPromptIsRejected(String prompt) throws Exception {
        var task = Agent.builder()
            .id("agent-blank-prompt-test")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue(CONFIG))
            .prompt(Property.ofExpression("{{ inputs.prompt }}"))
            .build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of("prompt", prompt));

        var exception = assertThrows(IllegalArgumentException.class, () -> task.buildAgentCommand(runContext, Path.of("agent.yaml")));

        assertThat(exception.getMessage(), is("prompt must not be blank for headless execution."));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "Review the release notes.",
            "Review 'quoted' and \"double quoted\" text.",
            "Keep $(touch unexpected) and `commands`; $HOME literal.",
            "First line\nSecond line",
            "--help",
            "--exec=false"
        }
    )
    void renderedPromptRemainsOneUnmodifiedArgument(String prompt) throws Exception {
        var task = Agent.builder()
            .id("agent-prompt-test")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue(CONFIG))
            .prompt(Property.ofExpression("{{ inputs.prompt }}"))
            .build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of("prompt", prompt));
        Path configPath = task.resolveAgentConfig(runContext);

        assertThat(
            task.buildAgentCommand(runContext, configPath),
            is(List.of("/docker-agent", "run", "--exec", "--", configPath.toString(), prompt))
        );
    }

    @Test
    void containerImageDefaultsToVersionedDockerAgentImage() throws Exception {
        var task = task(CONFIG);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThat(runContext.render(task.getContainerImage()).as(String.class).orElseThrow(), is("docker/docker-agent:1.146.0"));
    }

    @Test
    void containerImageOverrideIsRendered() throws Exception {
        var task = Agent.builder()
            .id("agent-image-test")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue(CONFIG))
            .prompt(Property.ofValue("Say hello."))
            .containerImage(Property.ofExpression("{{ inputs.image }}"))
            .build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of("image", "company/docker-agent:custom"));

        assertThat(runContext.render(task.getContainerImage()).as(String.class).orElseThrow(), is("company/docker-agent:custom"));
    }

    @Test
    void inheritedKillReachesConfiguredRunner() throws Exception {
        AtomicBoolean stopped = new AtomicBoolean();
        var runner = new CancellationProbeDocker(stopped);
        var task = Agent.builder()
            .id("agent-kill-test")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue(CONFIG))
            .prompt(Property.ofValue("Say hello."))
            .taskRunner(runner)
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var commands = task.commands(runContext);
        assertThat(commands.getTaskRunner(), sameInstance(runner));
        assertThat(task.getTaskRunner(), sameInstance(runner));

        task.kill();

        assertThat(stopped.get(), is(true));
    }

    @Test
    @Timeout(60)
    void inheritedKillStopsDefaultRunnerContainer() throws Exception {
        var task = task(CONFIG);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var commands = task.commands(runContext)
            .withCommands(Property.ofValue(List.of("/bin/sh", "-c", "echo 'Agent cancellation test started'; exec sleep 120")));
        assertThat(task.getTaskRunner(), sameInstance(commands.getTaskRunner()));

        CountDownLatch started = new CountDownLatch(1);
        Runnable stopReceiving = logQueue.receive(message ->
        {
            if (
                message.isLeft() && task.getId().equals(message.getLeft().getTaskId())
                    && message.getLeft().getMessage().equals("Agent cancellation test started")
            ) {
                started.countDown();
            }
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var execution = executor.submit(() -> commands.run());
            assertTrue(started.await(30, TimeUnit.SECONDS), "The default runner's container should start before cancellation.");

            task.kill();

            var exception = assertThrows(ExecutionException.class, () -> execution.get(10, TimeUnit.SECONDS));
            var taskException = (RunnableTaskException) exception.getCause();
            assertThat(((TaskException) taskException.getCause()).getExitCode(), not(is(0)));
        } finally {
            // Also clean up the real runner if an assertion fails before task.kill().
            commands.getTaskRunner().kill();
            stopReceiving.run();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "The cancelled runner should terminate.");
        }
    }

    @ParameterizedTest
    @CsvSource({ "Say hello., false", "--help, false", "Say hello., true" })
    @Timeout(60)
    void successfulExecutionReturnsScriptOutput(String prompt, boolean relativeConfig, WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/v1/chat/completions"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(
                            """
                                data: {"id":"test","object":"chat.completion.chunk","model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Agent execution succeeded."},"finish_reason":null}]}

                                data: {"id":"test","object":"chat.completion.chunk","model":"test-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                                data: [DONE]

                                """
                        )
                )
        );
        String config = """
            agents:
              root:
                model: test
                instruction: Answer the user's request.
            models:
              test:
                provider: openai
                model: test-model
                base_url: http://host.docker.internal:%d/v1
                token_key: OPENAI_API_KEY
                provider_opts:
                  api_type: openai_chatcompletions
            """.formatted(wm.getHttpPort());
        var task = Agent.builder()
            .id("agent-success-test")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue(relativeConfig ? "configs/agent.yaml" : config))
            .prompt(Property.ofValue(prompt))
            .env(Property.ofValue(Map.of("OPENAI_API_KEY", "test-api-key", "TELEMETRY_ENABLED", "false")))
            .taskRunner(dockerRunner())
            .build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        if (relativeConfig) {
            Path configPath = runContext.workingDir().resolve(Path.of("configs/agent.yaml"));
            Path instructionPath = configPath.getParent().resolve("instructions/root.md");
            Files.createDirectories(instructionPath.getParent());
            Files.writeString(instructionPath, "Follow the config-relative instruction file.");
            Files.writeString(configPath, config.replace("instruction: Answer the user's request.", "instruction_file: instructions/root.md"));
        }

        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        CountDownLatch answerLogged = new CountDownLatch(1);
        Runnable stopReceiving = logQueue.receive(message ->
        {
            if (message.isLeft() && task.getId().equals(message.getLeft().getTaskId())) {
                LogEntry entry = message.getLeft();
                logs.add(entry);
                if (entry.getMessage().contains("Agent execution succeeded.")) {
                    answerLogged.countDown();
                }
            }
        });
        ScriptOutput output;
        try {
            output = task.run(runContext);
            assertTrue(answerLogged.await(5, TimeUnit.SECONDS), "Agent stdout should reach the execution log queue.");
        } finally {
            stopReceiving.run();
        }

        if (relativeConfig) {
            verify(
                postRequestedFor(urlEqualTo("/v1/chat/completions"))
                    .withRequestBody(containing("Follow the config-relative instruction file."))
            );
        }
        assertThat(output.getExitCode(), is(0));
        assertThat(output.getStdOutLineCount(), greaterThan(0));
        String executionLogs = String.join("\n", logs.stream().map(LogEntry::getMessage).toList());
        assertThat(executionLogs, not(containsString("test-api-key")));
        verify(
            postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-api-key"))
                .withRequestBody(containing(prompt))
        );
    }

    @Test
    @Timeout(60)
    void nonzeroExecutionFailsTaskAndPreservesExitCode() throws Exception {
        var task = Agent.builder()
            .id("agent-failure-test")
            .type(Agent.class.getName())
            .agentConfig(Property.ofValue("agents: {}"))
            .prompt(Property.ofValue("Say hello."))
            .env(Property.ofValue(Map.of("TELEMETRY_ENABLED", "false")))
            .taskRunner(dockerRunner())
            .build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(RunnableTaskException.class, () -> task.run(runContext));

        int exitCode = ((TaskException) exception.getCause()).getExitCode();
        assertThat(exitCode, not(is(0)));
        assertThat(((ScriptOutput) exception.getOutput()).getExitCode(), is(exitCode));
    }

    private Docker dockerRunner() {
        return Docker.builder()
            .type(Docker.class.getName())
            .user("root")
            .extraHosts(
                System.getProperty("os.name").startsWith("Linux")
                    ? List.of("host.docker.internal:host-gateway")
                    : null
            )
            .build();
    }

    private static class CancellationProbeDocker extends Docker {
        private CancellationProbeDocker(AtomicBoolean stopped) {
            this.type = Docker.class.getName();
            onKill(() -> stopped.set(true));
        }
    }
}
