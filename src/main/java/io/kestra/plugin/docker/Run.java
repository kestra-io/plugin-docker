package io.kestra.plugin.docker;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.*;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.*;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a Docker container with runtime controls",
    description = "Starts a container using the Docker task runner or host daemon, with optional waits, resource limits, port/volume bindings, and registry credentials. Waits for exit by default; volume mounts are disabled unless enabled in `kestra.tasks.scripts.docker.volume-enabled`."
)
@Plugin(
    examples = {
        @Example(
            title = "Run the alpine container with no command",
            full = true,
            code = """
                id: docker_run
                namespace: company.team

                tasks:
                  - id: run
                    type: io.kestra.plugin.docker.Run
                    containerImage: alpine:latest
                """
        ),
        @Example(
            title = "Run the docker/opentelemetry with commands and config file",
            full = true,
            code = """
                id: docker_run
                namespace: company.team

                tasks:
                  - id: write
                    type: io.kestra.plugin.core.storage.Write
                       content: |
                         extensions:
                           health_check: {}

                         receivers:
                           otlp:
                             protocols:
                               grpc:
                                 endpoint: 0.0.0.0:4317
                               http:
                                 endpoint: 0.0.0.0:4318

                         exporters:
                           debug: {}

                         service:
                           pipelines:
                             logs:
                               receivers: [otlp]
                               exporters: [debug]
                       extension: .yaml

                  - id: run
                    type: io.kestra.plugin.docker.Run
                    containerImage: otel/opentelemetry-collector:latest
                    inputFiles:
                      otel.yaml: "{{ outputs.write.uri }}"
                    commands:
                      - --config
                      - otel.yaml
                    portBindings:
                      - "4318:4318"
                    wait: false
                """
        ),
        @Example(
            title = "Run Docker with Ubuntu image, run shell commands to create a file, log the output in Kestra",
            full = true,
            code = """
                id: docker_run_with_output_file
                namespace: company.team

                inputs:
                  - id: greetings
                    type: STRING
                    defaults: HELLO WORLD !!

                tasks:
                  - id: docker_run_output_file
                    type: io.kestra.plugin.docker.Run
                    containerImage: ubuntu:22.04
                    commands:
                      - "/bin/sh"
                      - "-c"
                      - echo {{ inputs.greetings }} > file.txt
                    outputFiles:
                      - file.txt

                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ read(outputs.docker_run_output_file.outputFiles['file.txt']) }}"
                """
        )
    }
)
public class Run extends AbstractDocker implements RunnableTask<ScriptOutput>, NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {
    @Schema(
        title = "Container image",
        description = "Image reference; if credentials include a registry, it is prepended when missing."
    )
    @NotNull
    protected Property<String> containerImage;

    @Schema(
        title = "Container user",
        description = "Optional user/UID to run the process as."
    )
    protected Property<String> user;

    @Schema(
        title = "Entrypoint",
        description = "Override the image entrypoint; empty keeps the image default."
    )
    protected Property<List<String>> entryPoint;

    @Schema(
        title = "Extra host entries",
        description = "List of `host:ip` mappings added to /etc/hosts."
    )
    protected Property<List<String>> extraHosts;

    @Schema(
        title = "Network mode",
        description = "Docker network mode such as `host`, `bridge`, or `none`."
    )
    protected Property<String> networkMode;

    @Schema(
        title = "List of port bindings",
        description = "Corresponds to the `--publish` (`-p`) option of the docker run CLI command using the format `ip:dockerHostPort:containerPort/protocol`. \n" +
            "Possible examples:\n" +
            "- `8080:80/udp`\n" +
            "- `127.0.0.1:8080:80`\n" +
            "- `127.0.0.1:8080:80/udp`"
    )
    @PluginProperty(dynamic = true)
    protected Property<List<String>> portBindings;

    @Schema(
        title = "List of volumes to mount",
        description = """
            Must be a valid mount expression as a string, for example: `/home/user:/app`.

            Volume mounts are disabled by default for security reasons; you must enable them on server configuration by setting `kestra.tasks.scripts.docker.volume-enabled` to `true`.
            """
    )
    @PluginProperty(dynamic = true)
    protected Property<List<String>> volumes;

    @Schema(
        title = "Image pull policy",
        description = "Defaults to IF_NOT_PRESENT; set to ALWAYS to refresh the image even when cached."
    )
    @Builder.Default
    protected Property<PullPolicy> pullPolicy = Property.ofValue(PullPolicy.IF_NOT_PRESENT);

    @Schema(
        title = "Device requests",
        description = "List of device requests forwarded to device drivers (e.g., GPUs)."
    )
    @PluginProperty
    protected List<DeviceRequest> deviceRequests;

    @Schema(
        title = "CPU limits",
        description = "Set CPU quota/shares; otherwise the container can use all host CPU."
    )
    @PluginProperty
    protected Cpu cpu;

    @Schema(
        title = "Memory limits",
        description = "Hard/soft memory limits; default is unlimited memory."
    )
    @PluginProperty
    protected Memory memory;

    @Schema(
        title = "Shared memory size",
        description = "Bytes for /dev/shm; defaults to 64MB when not set."
    )
    private Property<String> shmSize;

    @Schema(
        title = "Privileged container",
        description = "If true, runs the container with `--privileged`; use cautiously."
    )
    private Property<Boolean> privileged;

    @Schema(
        title = "Environment variables",
        description = "Key/value map rendered before launching the container."
    )
    private Property<Map<String, String>> env;

    @Schema(
        title = "Deprecated: warningOnStdErr",
        description = "Deprecated and ignored; will be removed in a future version."
    )
    @Deprecated
    private Property<Boolean> warningOnStdErr;

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    private Property<List<String>> outputFiles;

    @Schema(
        title = "Commands to run",
        description = "Command/args executed inside the container; empty uses the image CMD."
    )
    @Builder.Default
    private Property<List<String>> commands = Property.ofValue(new ArrayList<>());

    @Builder.Default
    @Schema(
        title = "Wait for container exit",
        description = "Defaults to true; set false to start and return immediately."
    )
    private final Property<Boolean> wait = Property.ofValue(true);

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        String image = runContext.render(this.containerImage).as(String.class).orElseThrow();
        String registry = Optional.ofNullable(this.getCredentials())
            .map(throwFunction(cred -> runContext.render(cred.getRegistry()).as(String.class).orElse(null)))
            .orElse(null);

        if (registry != null && !image.startsWith(registry)) {
            image = String.join("/", registry, image);
        }
        TaskRunner<Docker.DockerTaskRunnerDetailResult> taskRunner = Docker
            .builder()
            .type(Docker.class.getName())
            .host(runContext.render(this.host).as(String.class).orElse(null))
            .config(this.config)
            .credentials(this.credentials)
            .user(runContext.render(this.user).as(String.class).orElse(null))
            .entryPoint(runContext.render(this.entryPoint).asList(String.class).isEmpty() ? null : runContext.render(this.entryPoint).asList(String.class))
            .extraHosts(runContext.render(this.extraHosts).asList(String.class).isEmpty() ? null : runContext.render(this.extraHosts).asList(String.class))
            .networkMode(runContext.render(this.networkMode).as(String.class).orElse(null))
            .portBindings(runContext.render(this.portBindings).asList(String.class))
            .volumes(runContext.render(this.volumes).asList(String.class).isEmpty() ? null : runContext.render(this.volumes).asList(String.class))
            .pullPolicy(this.pullPolicy)
            .deviceRequests(this.deviceRequests)
            .cpu(this.cpu)
            .memory(this.memory)
            .shmSize(runContext.render(this.shmSize).as(String.class).orElse(null))
            .privileged(this.privileged)
            .wait(wait)
            .build();

        var renderedOutputFiles = runContext.render(this.outputFiles).asList(String.class);
        var commandWrapper = new CommandsWrapper(runContext)
            .withEnv(runContext.render(this.getEnv()).asMap(String.class, String.class).isEmpty() ? new HashMap<>() : runContext.render(this.getEnv()).asMap(String.class, String.class))
            .withContainerImage(image)
            .withTaskRunner(taskRunner)
            .withNamespaceFiles(this.namespaceFiles)
            .withInputFiles(this.inputFiles)
            .withOutputFiles(renderedOutputFiles.isEmpty() ? null : renderedOutputFiles)
            .withCommands(this.commands);

        return commandWrapper.run();
    }
}
