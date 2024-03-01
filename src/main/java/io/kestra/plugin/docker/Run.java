package io.kestra.plugin.docker;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
            title = "Run the docker/whalesay container with the commands 'cowsay hello'",
            code = {"""
                docker:
                  image: docker/whalesay
                  commands:
                    - cowsay
                    - hello"""
            }
        ),
        @Example(
            title = "Run the docker/whalesay container with no commands",
            code = {"""
                docker:
                  image: docker/whalesay"""
            }
        )
    }
)
public class Run extends Task implements RunnableTask<ScriptOutput>, NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {
    @Schema(
        title = "Docker options"
    )
    @PluginProperty
    @NotNull
    private DockerOptions docker;

    @Schema(
        title = "Additional environment variables for the Docker container."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> env;

    @Builder.Default
    @Schema(
        title = "Whether to set the task state to `WARNING` if any `stdErr` is emitted."
    )
    @PluginProperty
    protected Boolean warningOnStdErr = true;

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
        var commandWrapper = new CommandsWrapper(runContext)
            .withEnv(this.getEnv())
            .withWarningOnStdErr(this.getWarningOnStdErr())
            .withRunnerType(RunnerType.DOCKER)
            .withDockerOptions(this.docker)
            .withNamespaceFiles(this.namespaceFiles)
            .withInputFiles(this.inputFiles)
            .withOutputFiles(this.outputFiles)
            .withCommands(this.commands);
        
        return commandWrapper.run();
    }
}
