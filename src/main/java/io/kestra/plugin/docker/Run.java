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
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a Docker container"
)
@Plugin(
    examples = {
        @Example(
            title = "Run the docker/whalesay container with the command 'cowsay hello'",
            full = true,
            code = """
                id: docker_run
                namespace: company.team

                tasks:
                  - id: run
                    type: io.kestra.plugin.docker.Run
                    containerImage: docker/whalesay
                    commands:
                      - cowsay
                      - hello
                """
        ),
        @Example(
            title = "Run the docker/whalesay container with no command",
            full = true,
            code = """
                id: docker_run
                namespace: company.team

                tasks:
                  - id: run
                    type: io.kestra.plugin.docker.Run
                    containerImage: docker/whalesay
                """
        )
    }
)
public class Run extends AbstractDocker implements RunnableTask<ScriptOutput>, NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {
    @Schema(
        title = "Docker image to use."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    protected String containerImage;

    @Schema(
        title = "User in the Docker container."
    )
    protected Property<String> user;

    @Schema(
        title = "Docker entrypoint to use."
    )
    protected Property<List<String>> entryPoint;

    @Schema(
        title = "Extra hostname mappings to the container network interface configuration."
    )
    protected Property<List<String>> extraHosts;

    @Schema(
        title = "Docker network mode to use e.g. `host`, `none`, etc."
    )
    protected Property<String> networkMode;

    @Schema(
        title = "List of port bindings.",
        description = "Corresponds to the --publish (-p) option of the docker run CLI command using the format `ip:dockerHostPort:containerPort/protocol`. Possible example : \n" +
            "- 8080:80/udp" +
            "- 127.0.0.1:8080:80" +
            "- 127.0.0.1:8080:80/udp"
    )
    @PluginProperty(dynamic = true)
    protected Property<List<String>> portBindings;

    @Schema(
        title = "List of volumes to mount.",
        description = "Must be a valid mount expression as string, example : `/home/user:/app`.\n\n" +
            "Volumes mount are disabled by default for security reasons; you must enable them on server configuration by setting `kestra.tasks.scripts.docker.volume-enabled` to `true`."
    )
    @PluginProperty(dynamic = true)
    protected Property<List<String>> volumes;

    @Schema(
        title = "The pull policy for an image.",
        description = "Pull policy can be used to prevent pulling of an already existing image `IF_NOT_PRESENT`, or can be set to `ALWAYS` to pull the latest version of the image even if an image with the same tag already exists."
    )
    @Builder.Default
    protected Property<PullPolicy> pullPolicy = Property.of(PullPolicy.ALWAYS);

    @Schema(
        title = "A list of device requests to be sent to device drivers."
    )
    @PluginProperty
    protected List<DeviceRequest> deviceRequests;

    @Schema(
        title = "Limits the CPU usage to a given maximum threshold value.",
        description = "By default, each container’s access to the host machine’s CPU cycles is unlimited. " +
            "You can set various constraints to limit a given container’s access to the host machine’s CPU cycles."
    )
    @PluginProperty
    protected Cpu cpu;

    @Schema(
        title = "Limits memory usage to a given maximum threshold value.",
        description = "Docker can enforce hard memory limits, which allow the container to use no more than a " +
            "given amount of user or system memory, or soft limits, which allow the container to use as much " +
            "memory as it needs unless certain conditions are met, such as when the kernel detects low memory " +
            "or contention on the host machine. Some of these options have different effects when used alone or " +
            "when more than one option is set."
    )
    @PluginProperty
    protected Memory memory;

    @Schema(
        title = "Size of `/dev/shm` in bytes.",
        description = "The size must be greater than 0. If omitted, the system uses 64MB."
    )
    private Property<String> shmSize;

    @Schema(
        title = "Give extended privileges to this container."
    )
    private Property<Boolean> privileged;

    @Schema(
        title = "Additional environment variables for the Docker container."
    )
    private Property<Map<String, String>> env;

    @Builder.Default
    @Schema(
        title = "Whether to set the task state to `WARNING` if any `stdErr` is emitted."
    )
    private Property<Boolean> warningOnStdErr = Property.of(true);

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    private Property<List<String>> outputFiles;

    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<List<String>> commands = Property.of(new ArrayList<>());

    @Builder.Default
    @Schema(
        title = "Whether to wait for the container to exit, or simply start it."
    )
    @PluginProperty
    private final Boolean wait = true;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
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
            .pullPolicy(runContext.render(this.pullPolicy).as(PullPolicy.class).orElseThrow())
            .deviceRequests(this.deviceRequests)
            .cpu(this.cpu)
            .memory(this.memory)
            .shmSize(runContext.render(this.shmSize).as(String.class).orElse(null))
            .privileged(runContext.render(this.privileged).as(Boolean.class).orElse(null))
            .wait(wait)
            .build();

        var renderedOutputFiles = runContext.render(this.outputFiles).asList(String.class);
        var commandWrapper = new CommandsWrapper(runContext)
            .withEnv(runContext.render(this.getEnv()).asMap(String.class, String.class).isEmpty() ? new HashMap<>() : runContext.render(this.getEnv()).asMap(String.class, String.class))
            .withContainerImage(this.containerImage)
            .withTaskRunner(taskRunner)
            .withWarningOnStdErr(runContext.render(this.getWarningOnStdErr()).as(Boolean.class).orElseThrow())
            .withNamespaceFiles(this.namespaceFiles)
            .withInputFiles(this.inputFiles)
            .withOutputFiles(renderedOutputFiles.isEmpty() ? null : renderedOutputFiles)
            .withCommands(runContext.render(this.commands).asList(String.class).isEmpty() ? null : runContext.render(this.commands).asList(String.class));

        return commandWrapper.run();
    }
}
