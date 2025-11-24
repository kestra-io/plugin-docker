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
@Schema(title = "Run Docker Compose commands.")
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

    @Schema(
        title = "The compose file (can be passed as an inline YAML, a path in working directory, or a Kestra URI)"
    )
    @NotNull
    @PluginProperty(internalStorageURI = true)
    private Property<String> composeFile;

    @Schema(
        title = "Arguments passed AFTER `docker compose -f <file>`",
        description = "Example: ['up', '-d'], ['logs', '-f'], ['exec', 'web', 'ls']"
    )
    @NotNull
    @PluginProperty
    private Property<List<String>> composeArgs;

    // Force using host process runner
    @Builder.Default
    @PluginProperty
    protected TaskRunner<?> taskRunner = Process.builder()
        .type(Process.class.getName())
        .build();

    @Override
    public Property<String> getContainerImage() {
        return Property.ofValue(null);
    }

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
            .withTaskRunner(this.taskRunner)
            .withCommands(Property.ofValue(args))
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
