package io.kestra.plugin.docker.cli;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class Agent extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    @Schema(
        title = "Container image",
        description = "Execution image containing the Docker Agent executable."
    )
    @PluginProperty(group = "execution")
    protected Property<String> containerImage;

    @Schema(
        title = "Agent configuration",
        description = "Docker Agent configuration supplied as inline YAML, a relative path in the working directory, or a `kestra://` internal-storage URI."
    )
    @NotNull
    @PluginProperty(internalStorageURI = true, group = "main")
    private Property<String> agentConfig;

    @Schema(
        title = "Prompt",
        description = "Optional initial assignment passed to the agent for this execution."
    )
    @PluginProperty(group = "main")
    private Property<String> prompt;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        String renderedPrompt = runContext.render(this.prompt).as(String.class).orElse(null);
        String renderedContainerImage = runContext.render(this.containerImage).as(String.class).orElse(null);
        Path configPath = resolveAgentConfig(runContext);

        throw new UnsupportedOperationException("Docker Agent execution is not implemented yet.");
    }

    Path resolveAgentConfig(RunContext runContext) throws Exception {
        String config = runContext.render(this.agentConfig).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("agentConfig is required."));
        if (config.isBlank()) {
            throw new IllegalArgumentException("agentConfig must not be blank.");
        }

        var workingDir = runContext.workingDir();
        String source = config.strip();

        if (source.startsWith("kestra://")) {
            Path tempFile = workingDir.createTempFile(".yaml");
            try (InputStream input = runContext.storage().getFile(URI.create(source))) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        }

        // Agent configuration is a YAML mapping; recognize block and flow forms.
        boolean inlineYaml = source.contains("\n") || source.contains("\r")
            || source.startsWith("{") || source.matches("^[^:]+:(\\s.*)?$");
        if (inlineYaml) {
            return workingDir.createTempFile(config.getBytes(StandardCharsets.UTF_8), ".yaml");
        }

        Path relativePath = Path.of(source);
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("agentConfig file paths must be relative to the working directory.");
        }

        Path sourceFile = workingDir.resolve(relativePath);
        if (!Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("agentConfig file does not exist or is not a regular file: " + source);
        }

        Path tempFile = workingDir.createTempFile(".yaml");
        Files.copy(sourceFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }
}
