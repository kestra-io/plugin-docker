package io.kestra.plugin.docker.cli;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a Docker Agent team",
    description = """
        Runs a Docker Agent team non-interactively using `/docker-agent run --exec` through the configured task runner.
        Supply the team configuration as inline YAML, a relative path in the task working directory, or a `kestra://` URI.
        Existing relative configurations are passed directly to preserve config-relative references. Inline YAML and `kestra://` configurations use temporary YAML files.
        Pass model provider API keys through `env` using Kestra secrets.
        The default image is `docker/docker-agent:1.146.0`; custom images must provide the executable at `/docker-agent`.
        Set the Docker task runner `user` to `root` with the default image so it can read Kestra temporary files.
        Agent stdout and stderr are streamed to the execution logs. Returns `ScriptOutput`, including the exit code and configured output files.
        """
)
@Plugin(
    aliases = "io.kestra.plugin.docker.Agent",
    examples = {
        @Example(
            title = "Run an agent team from inline configuration",
            full = true,
            code = """
                id: docker_agent_review
                namespace: company.team

                tasks:
                  - id: review
                    type: io.kestra.plugin.docker.cli.Agent
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                      user: root
                    env:
                      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
                    prompt: "Review these release notes for breaking changes: v2 removes the legacy /v1 API and adds CSV export."
                    agentConfig: |
                      agents:
                        root:
                          model: openai/gpt-5
                          instruction: You review release notes for breaking changes.
                """
        ),
        @Example(
            title = "Run an agent team from an uploaded configuration file",
            full = true,
            code = """
                id: docker_agent_from_file
                namespace: company.team

                inputs:
                  - id: config
                    type: FILE

                tasks:
                  - id: run_team
                    type: io.kestra.plugin.docker.cli.Agent
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                      user: root
                    env:
                      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
                    agentConfig: "{{ inputs.config }}"
                    prompt: "Create a checklist for reviewing a production deployment."

                  - id: log_result
                    type: io.kestra.plugin.core.log.Log
                    message: "Agent exit code: {{ outputs.run_team.exitCode }}"
                """
        ),
        @Example(
            title = "Run an agent team on a daily schedule",
            full = true,
            code = """
                id: docker_agent_daily
                namespace: company.team

                triggers:
                  - id: every_morning
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 7 * * *"

                tasks:
                  - id: daily_checklist
                    type: io.kestra.plugin.docker.cli.Agent
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                      user: root
                    env:
                      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
                    prompt: "Create a short checklist for today's deployment review."
                    agentConfig: |
                      agents:
                        root:
                          model: openai/gpt-5
                          instruction: You write concise operations checklists.
                """
        ),
        @Example(
            title = "Pass an upstream output file to the agent through the prompt",
            full = true,
            code = """
                id: docker_agent_summarize_file
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: summarize
                    type: io.kestra.plugin.docker.cli.Agent
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                      user: root
                    env:
                      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
                    prompt: "Summarize the orders in {{ outputs.download.uri }}."
                    agentConfig: |
                      agents:
                        root:
                          model: openai/gpt-5
                          instruction: You summarize CSV files.
                          toolsets:
                            - type: filesystem
                """
        )
    }
)
public class Agent extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "docker/docker-agent:1.146.0";
    // A single-line YAML mapping such as `agents:` or `agents: {root: ...}`; a colon must be followed by whitespace or end the line.
    private static final Pattern SINGLE_LINE_YAML_MAPPING = Pattern.compile("^[^:]+:(\\s.*)?$");

    @Builder.Default
    @Schema(
        title = "Container image",
        description = "Execution image containing the Docker Agent executable at `/docker-agent`. Custom images must provide the same executable path.",
        defaultValue = DEFAULT_IMAGE
    )
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Agent configuration",
        description = "Docker Agent configuration supplied as inline YAML, a relative path in the working directory, or a `kestra://` internal-storage URI."
    )
    @NotNull
    @PluginProperty(internalStorageURI = true, group = "main")
    private Property<String> agentConfig;

    @Schema(
        title = "Prompt",
        description = """
            Required non-blank initial assignment for headless execution. YAML instructions define agent behavior and do not replace this message.
            Any `kestra://` URI in the prompt is downloaded into the working directory and replaced with its local path before execution; a missing or malformed URI fails the task.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> prompt;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final transient AtomicReference<TaskRunner<?>> activeRunner = new AtomicReference<>();

    @Override
    protected CommandsWrapper commands(RunContext runContext) throws IllegalVariableEvaluationException {
        var commands = super.commands(runContext);
        var runner = commands.getTaskRunner();
        this.activeRunner.set(runner);
        // Legacy Docker options have already been applied; reuse the same runner during execution.
        return commands.withTaskRunner(runner).withDockerOptions(null);
    }

    // Inherited kill() cannot reach the default runner when taskRunner is unconfigured.
    @Override
    public void kill() {
        var runner = this.activeRunner.get();
        if (runner != null) {
            runner.kill();
        } else {
            super.kill();
        }
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var configPath = resolveAgentConfig(runContext);
        var command = buildAgentCommand(runContext, configPath);

        return this.commands(runContext)
            .withCommands(Property.ofValue(command))
            .run();
    }

    List<String> buildAgentCommand(RunContext runContext, Path configPath) throws Exception {
        var arguments = new ArrayList<>(List.of("/docker-agent", "run", "--exec", "--", configPath.toString()));
        var renderedPrompt = runContext.render(this.prompt).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("prompt is required for headless execution."));
        if (renderedPrompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank for headless execution.");
        }
        arguments.add(renderedPrompt);
        return List.copyOf(arguments);
    }

    Path resolveAgentConfig(RunContext runContext) throws Exception {
        var config = runContext.render(this.agentConfig).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("agentConfig is required."));
        if (config.isBlank()) {
            throw new IllegalArgumentException("agentConfig must not be blank.");
        }

        var workingDir = runContext.workingDir();
        var source = config.strip();

        if (source.startsWith(StorageContext.KESTRA_PROTOCOL)) {
            var tempFile = workingDir.createTempFile(".yaml");
            try (var input = runContext.storage().getFile(URI.create(source))) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        }

        var inlineYaml = source.contains("\n") || source.contains("\r")
            || source.startsWith("{") || SINGLE_LINE_YAML_MAPPING.matcher(source).matches();
        if (inlineYaml) {
            return workingDir.createTempFile(config.getBytes(StandardCharsets.UTF_8), ".yaml");
        }

        var relativePath = Path.of(source);
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("agentConfig file paths must be relative to the working directory.");
        }

        var sourceFile = workingDir.resolve(relativePath);
        if (!Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException(
                "agentConfig must be inline YAML, a kestra:// URI, or an existing relative file in the working directory: " + source
            );
        }

        return sourceFile;
    }
}
