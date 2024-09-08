package io.kestra.plugin.docker;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
public class Run extends Task implements RunnableTask<ScriptOutput>, NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {
    @Schema(
        title = "Docker API URI."
    )
    @PluginProperty(dynamic = true)
    private String host;

    @Schema(
        title = "Docker configuration file.",
        description = "Docker configuration file that can set access credentials to private container registries. Usually located in `~/.docker/config.json`.",
        anyOf = {String.class, Map.class}
    )
    @PluginProperty(dynamic = true)
    private Object config;

    @Schema(
        title = "Credentials for a private container registry."
    )
    @PluginProperty(dynamic = true)
    private Credentials credentials;

    @Schema(
        title = "Docker image to use."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    protected String containerImage;

    @Schema(
        title = "User in the Docker container."
    )
    @PluginProperty(dynamic = true)
    protected String user;

    @Schema(
        title = "Docker entrypoint to use."
    )
    @PluginProperty(dynamic = true)
    protected List<String> entryPoint;

    @Schema(
        title = "Extra hostname mappings to the container network interface configuration."
    )
    @PluginProperty(dynamic = true)
    protected List<String> extraHosts;

    @Schema(
        title = "Docker network mode to use e.g. `host`, `none`, etc."
    )
    @PluginProperty(dynamic = true)
    protected String networkMode;

    @Schema(
        title = "List of volumes to mount.",
        description = "Must be a valid mount expression as string, example : `/home/user:/app`.\n\n" +
            "Volumes mount are disabled by default for security reasons; you must enable them on server configuration by setting `kestra.tasks.scripts.docker.volume-enabled` to `true`."
    )
    @PluginProperty(dynamic = true)
    protected List<String> volumes;

    @Schema(
        title = "The pull policy for an image.",
        description = "Pull policy can be used to prevent pulling of an already existing image `IF_NOT_PRESENT`, or can be set to `ALWAYS` to pull the latest version of the image even if an image with the same tag already exists."
    )
    @PluginProperty
    @Builder.Default
    protected PullPolicy pullPolicy = PullPolicy.ALWAYS;

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
    @PluginProperty(dynamic = true)
    private String shmSize;

    @Schema(
        title = "Additional environment variables for the Docker container."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    private Map<String, String> env;

    @Builder.Default
    @Schema(
        title = "Whether to set the task state to `WARNING` if any `stdErr` is emitted."
    )
    @PluginProperty
    private Boolean warningOnStdErr = true;

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    private List<String> outputFiles;

    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private List<String> commands = Collections.emptyList();

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        TaskRunner taskRunner = Docker
            .builder()
            .type(Docker.class.getName())
            .host(this.host)
            .config(this.config)
            .credentials(this.credentials)
            .user(this.user)
            .entryPoint(this.entryPoint)
            .extraHosts(this.extraHosts)
            .networkMode(this.networkMode)
            .volumes(this.volumes)
            .pullPolicy(this.pullPolicy)
            .deviceRequests(this.deviceRequests)
            .cpu(this.cpu)
            .memory(this.memory)
            .shmSize(this.shmSize)
            .build();

        var commandWrapper = new CommandsWrapper(runContext)
            .withEnv(this.getEnv())
            .withContainerImage(this.containerImage)
            .withTaskRunner(taskRunner)
            .withWarningOnStdErr(this.getWarningOnStdErr())
            .withNamespaceFiles(this.namespaceFiles)
            .withInputFiles(this.inputFiles)
            .withOutputFiles(this.outputFiles)
            .withCommands(this.commands);

        return commandWrapper.run();
    }
}
