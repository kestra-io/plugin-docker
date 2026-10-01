package io.kestra.plugin.docker.cli;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
        Runs Docker Agent in non-interactive mode through the configured task runner.
        The agent configuration can be provided inline, as a relative path in the working
        directory, or as a `kestra://` URI. Provider credentials should be supplied through
        the inherited `env` property.
        """
)
@Plugin(
    aliases = "io.kestra.plugin.docker.Agent",
    examples = {
        @Example(
            title = "Run a Docker Agent team with an inline configuration",
            full = true,
            code = """
                id: docker_agent_review
                namespace: company.team

                tasks:
                  - id: review
                    type: io.kestra.plugin.docker.cli.Agent
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                    env:
                      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
                    prompt: "Review the release notes and list breaking changes."
                    agentConfig: |
                      agents:
                        root:
                          model: openai/gpt-5
                          description: Release notes reviewer
                          instruction: You review release notes for breaking changes.
                """
        )
    }
)
public class Agent extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "docker/docker-agent:1.145.0";

    @Schema(
        title = "Docker Agent configuration",
        description = """
            Inline YAML, a relative path in the working directory, or a `kestra://` URI.
            Inline content and internal-storage files are materialized into the task working
            directory before execution.
            """
    )
    @NotNull
    @PluginProperty(internalStorageURI = true, group = "main")
    private Property<String> agentConfig;

    @Schema(
        title = "Prompt",
        description = "The message sent to the Docker Agent team. It is passed through standard input so prompt contents are not interpreted as shell syntax."
    )
    @PluginProperty(group = "main")
    private Property<String> prompt;

    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        Path configPath = resolveAgentConfig(runContext);
        String renderedPrompt = runContext.render(this.prompt).as(String.class).orElse(null);

        List<String> command = new ArrayList<>();
        command.add("/docker-agent");
        command.add("run");
        command.add("--exec");
        command.add(shellQuote(configPath.toString()));

        if (renderedPrompt != null) {
            Path promptPath = runContext.workingDir().createTempFile(
                renderedPrompt.getBytes(StandardCharsets.UTF_8),
                ".txt"
            );

            command.add("-");
            command.add("<");
            command.add(shellQuote(promptPath.toString()));
        }

        runContext.logger().info("Running Docker Agent with config: {}", configPath);

        return this.commands(runContext)
            .withCommands(Property.ofValue(List.of(String.join(" ", command))))
            .withTargetOS(runContext.render(this.targetOS).as(TargetOS.class).orElse(null))
            .run();
    }

    private Path resolveAgentConfig(RunContext runContext) throws Exception {
        String renderedConfig = runContext.render(this.agentConfig).as(String.class).orElse(null);
        if (renderedConfig == null || renderedConfig.isBlank()) {
            throw new IllegalArgumentException("The 'agentConfig' property must not be empty.");
        }

        Path workingDir = runContext.workingDir().path();

        if (renderedConfig.startsWith("kestra://")) {
            Path tempFile = runContext.workingDir().createTempFile(".yaml");
            try (InputStream in = runContext.storage().getFile(URI.create(renderedConfig))) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        }

        Path candidate = Path.of(renderedConfig);
        if (!candidate.isAbsolute()) {
            Path relativePath = workingDir.resolve(candidate);
            if (Files.isRegularFile(relativePath)) {
                return relativePath;
            }
        }

        return runContext.workingDir().createTempFile(
            renderedConfig.getBytes(StandardCharsets.UTF_8),
            ".yaml"
        );
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
