package io.kestra.plugin.docker;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run Docker Compose commands."
)
@Plugin(
    examples = {
        @Example(
            title = "Run docker compose up -d using an inline compose file",
            full = true,
            code = """
                id: docker_compose_up
                namespace: company.team

                tasks:
                  - id: up
                    type: io.kestra.plugin.docker.Compose
                    composeFile: |
                      services:
                        web:
                          image: nginx:alpine
                    command: UP
                    detach: true
                """
        ),
        @Example(
            title = "Run docker compose down using a compose.yaml file from namespace files",
            full = true,
            code = """
                id: docker_compose_down
                namespace: company.team

                tasks:
                  - id: down
                    type: io.kestra.plugin.docker.Compose
                    composeFile: compose.yaml
                    command: DOWN
                """
        )
    }
)
public class Compose extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    @Schema(
        title = "The contents of your docker-compose file passed as a string, " +
            "a path to a compose file in the working directory, or a Kestra internal URI."
    )
    @NotNull
    @PluginProperty(internalStorageURI = true)
    private Property<String> composeFile;

    @Builder.Default
    @PluginProperty
    protected TaskRunner<?> taskRunner = Process.builder()
        .type(Process.class.getName())
        .build();

    @Schema(
        title = "The docker compose command to run.",
        description = "Currently supported: UP (docker compose up) and DOWN (docker compose down)."
    )
    @Builder.Default
    private Property<Command> command = Property.ofValue(Command.UP);

    @Schema(
        title = "Whether to detach containers when running `docker compose up`.",
        description = "Corresponds to the `-d` flag."
    )
    @Builder.Default
    private Property<Boolean> detach = Property.ofValue(true);

    @Schema(
        title = "Optional list of services to target.",
        description = "If empty, the command applies to all services."
    )
    private Property<List<String>> services;

    @Schema(
        title = "Optional docker compose project name.",
        description = "Corresponds to the `-p` / `--project-name` flag."
    )
    private Property<String> projectName;

    @Override
    public Property<String> getContainerImage() {
        return Property.ofValue(null);
    }

    public enum Command {
        UP,
        DOWN
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        Path composePath = resolveComposeFile(runContext);

        var rCommand = runContext.render(this.command).as(Command.class).orElse(Command.UP);
        boolean detachFlag = runContext.render(this.detach).as(Boolean.class).orElse(false);
        List<String> renderedServices = runContext.render(this.services).asList(String.class);
        String renderedProjectName = runContext.render(this.projectName).as(String.class).orElse(null);

        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("compose");
        args.add("-f");
        args.add(composePath.toString());

        if (renderedProjectName != null && !renderedProjectName.isBlank()) {
            args.add("-p");
            args.add(renderedProjectName);
        }

        switch (rCommand) {
            case UP -> {
                args.add("up");
                if (detachFlag) {
                    args.add("-d");
                }
                if (!renderedServices.isEmpty()) {
                    args.addAll(renderedServices);
                }
            }
            case DOWN -> args.add("down");
        }

        String commandLine = String.join(" ", args);

        runContext.logger().info("Running command: {}", commandLine);

        return this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withTaskRunner(this.taskRunner)
            .withBeforeCommands(this.beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(List.of(commandLine)))
            .run();

    }

    private Path resolveComposeFile(RunContext runContext) throws Exception {
        var workingDir = runContext.workingDir();
        var rComposeFile = runContext.render(this.composeFile).as(String.class).orElseThrow();

        if (rComposeFile.startsWith("kestra://")) {
            Path tempFile = workingDir.createTempFile(".yaml");
            try (InputStream in = runContext.storage().getFile(URI.create(rComposeFile))) {
                java.nio.file.Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        }

        Path candidate = workingDir.resolve(java.nio.file.Path.of(rComposeFile));
        if (candidate.toFile().exists()) {
            return candidate;
        }

        return workingDir.createTempFile(
            rComposeFile.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            ".yaml"
        );
    }
}
