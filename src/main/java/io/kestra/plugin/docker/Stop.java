package io.kestra.plugin.docker;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.runner.docker.DockerService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Stop or kill a Docker container",
    description = "Stops a running container by default, or kills it when `kill` is true, then optionally deletes it. Defaults: kill=false, delete=true."
)
@Plugin(
    examples = {
        @Example(
            title = "Kill a Docker container",
            full = true,
            code = """
                id: docker_stop
                namespace: company.team

                tasks:
                  - id: run
                    type: io.kestra.plugin.docker.Stop
                    containerId: 8088357a1974
                    kill: true
                """
        )
    }
)
public class Stop extends AbstractDocker implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Container ID",
        description = "ID of the container to stop or kill."
    )
    private Property<String> containerId;

    @Schema(
        title = "Kill instead of stop",
        description = "When true, sends SIGKILL; otherwise uses a graceful stop."
    )
    @Builder.Default
    @NotNull
    private Property<Boolean> kill = Property.ofValue(false);

    @Schema(
        title = "Delete container after stop",
        description = "Defaults to true; set false to keep the container after stopping/killing."
    )
    @Builder.Default
    @NotNull
    private Property<Boolean> delete = Property.ofValue(true);

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        try (var client = DockerService.client(runContext, runContext.render(host).as(String.class).orElse(null), config, credentials, null)) {
            if (!runContext.render(kill).as(Boolean.class).orElseThrow()) {
                client.stopContainerCmd(runContext.render(containerId).as(String.class).orElseThrow()).exec();
            } else {
                client.killContainerCmd(runContext.render(containerId).as(String.class).orElseThrow()).exec();
            }

            if (runContext.render(delete).as(Boolean.class).orElseThrow()) {
                client.removeContainerCmd(runContext.render(containerId).as(String.class).orElseThrow()).exec();
            }
        }

        return null;
    }
}
