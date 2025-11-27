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
    title = "Run Docker Compose commands.",
    description = """
        This task runs `docker compose` using Kestra’s task runner.
        To work correctly, the runner must have access to a Docker daemon.

        ## To enable volume mounting for Docker task runner (disabled by default)

        ```yaml
        plugins:
          configurations:
            - type: io.kestra.plugin.scripts.runner.docker.Docker
              values:
                volume-enabled: true
        ```

        ## Docker task runner
        Mount the Docker socket so the runner can talk to the host engine:

        ```yaml
        taskRunner:
          type: io.kestra.plugin.scripts.runner.docker.Docker
          volumes:
            - /var/run/docker.sock:/var/run/docker.sock
        ```

        ## Process runner
        Run the Compose task on a worker where Docker is available in the PATH.

        ## On Kubernetes
        The worker must provide access to a Docker daemon (e.g., via a DinD sidecar).
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
        title = "The compose file (can be passed as an inline YAML, a path in working directory, or a Kestra URI)"
    )
    @NotNull
    @PluginProperty(internalStorageURI = true)
    private Property<String> composeFile;

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Arguments passed AFTER `docker compose -f <file>`",
        description = "Example: ['up', '-d'], ['logs', '-f'], ['exec', 'web', 'ls']"
    )
    @NotNull
    @PluginProperty
    private Property<List<String>> composeArgs;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        Path composePath = resolveComposeFile(runContext);

        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("compose");
        args.add("-f");
        args.add(composePath.toString());
        args.addAll(runContext.render(composeArgs).asList(String.class));

        runContext.logger().info("Running command: {}", String.join(" ", args));

        return this.commands(runContext)
            .withCommands(Property.ofValue(args))
            .withTargetOS(runContext.render(this.targetOS).as(TargetOS.class).orElse(null))
            .run();
    }

    private Path resolveComposeFile(RunContext runContext) throws Exception {
        var workingDir = runContext.workingDir();
        var rComposeFile = runContext.render(this.composeFile).as(String.class).orElseThrow();

        if (rComposeFile.startsWith("kestra://")) {
            Path tempFile = workingDir.createTempFile(".yaml");
            try (InputStream in = runContext.storage().getFile(URI.create(rComposeFile))) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        }

        Path candidate = workingDir.resolve(Path.of(rComposeFile));
        if (candidate.toFile().exists()) {
            return candidate;
        }

        return workingDir.createTempFile(
            rComposeFile.getBytes(StandardCharsets.UTF_8),
            ".yaml"
        );
    }
}
