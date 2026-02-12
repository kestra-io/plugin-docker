package io.kestra.plugin.docker;

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
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run Docker Compose with inline or stored files",
    description = """
        Runs `docker compose` via the configured task runner. The runner must reach a Docker daemon (mount the host socket or provide DinD). Volume mounting is disabled by default for the Docker task runner; enable `volume-enabled: true` if you need it.

        To enable volume mounting for Docker task runner (disabled by default):

        ```yaml
        plugins:
          configurations:
            - type: io.kestra.plugin.scripts.runner.docker.Docker
              values:
                volume-enabled: true
        ```

        Docker task runner: mount the Docker socket so the runner can talk to the host engine:

        ```yaml
        taskRunner:
          type: io.kestra.plugin.scripts.runner.docker.Docker
          volumes:
            - /var/run/docker.sock:/var/run/docker.sock
        ```

        On Kubernetes, ensure the worker has daemon access (e.g., DinD sidecar).
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Run `docker compose up -d` using an inline compose file",
            full = true,
            code = """
                id: docker_compose_up
                namespace: company.team

                tasks:
                  - id: up
                    type: io.kestra.plugin.docker.Compose
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                      volumes:
                        - /var/run/docker.sock:/var/run/docker.sock
                    composeFile: |
                      services:
                        web:
                          image: nginx:alpine
                    composeArgs:
                      - up
                      - -d
                """
        )
    }
)
public class Compose extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "docker:latest";

    @Schema(
        title = "Compose file",
        description = "Inline YAML, relative path in the working directory, or a `kestra://` URI; inline content is written to a temp file before execution."
    )
    @PluginProperty(internalStorageURI = true)
    private Property<String> composeFile;

    @Schema(
        title = "Compose files",
        description = "Optional list passed in order with repeated `-f` flags; supports inline, relative paths, or `kestra://` URIs."
    )
    @PluginProperty(internalStorageURI = true)
    private Property<List<String>> composeFiles;

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Compose command arguments",
        description = "Arguments appended after `docker compose -f <file>` such as `['up','-d']` or `['logs','-f']`; order is preserved."
    )
    @NotNull
    @PluginProperty
    private Property<List<String>> composeArgs;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var composePaths = resolveComposeFiles(runContext);

        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("compose");

        for (Path path : composePaths) {
            args.add("-f");
            args.add(path.toString());
        }

        args.addAll(runContext.render(composeArgs).asList(String.class));

        runContext.logger().info("Running command: {}", String.join(" ", args));

        return this.commands(runContext)
            .withCommands(Property.ofValue(args))
            .withTargetOS(runContext.render(this.targetOS).as(TargetOS.class).orElse(null))
            .run();
    }

    private List<Path> resolveComposeFiles(RunContext runContext) throws Exception {
        var workingDir = runContext.workingDir();

        var rComposeFiles = runContext.render(this.composeFiles).asList(String.class);

        if (rComposeFiles == null || rComposeFiles.isEmpty()) {
            var rComposeFile = runContext.render(this.composeFile).as(String.class).orElse(null);
            if (rComposeFile == null) {
                return List.of();
            }
            rComposeFiles = List.of(rComposeFile);
        }

        List<Path> paths = new ArrayList<>();
        for (String composeFile : rComposeFiles) {

            if (composeFile.startsWith("kestra://")) {
                Path tempFile = workingDir.createTempFile(".yaml");
                try (InputStream in = runContext.storage().getFile(URI.create(composeFile))) {
                    Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                paths.add(tempFile);
                continue;
            }

            Path candidate = workingDir.resolve(Path.of(composeFile));
            if (candidate.toFile().exists()) {
                paths.add(candidate);
                continue;
            }

            paths.add(workingDir.createTempFile(composeFile.getBytes(StandardCharsets.UTF_8), ".yaml"));
        }

        return paths;
    }
}
