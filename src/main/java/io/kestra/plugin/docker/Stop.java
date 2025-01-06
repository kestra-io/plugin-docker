package io.kestra.plugin.docker;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.runner.docker.DockerService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Stop a Docker container"
)
@Plugin(
    examples = {
        @Example(
            title = "Kill a docker container",
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
        title = "The container id to stop."
    )
    private Property<String> containerId;

    @Schema(title = "Does a kill or a stop command will be used.")
    @Builder.Default
    private Property<Boolean> kill = Property.of(false);


    @Schema(title = "Does we will remove the container.")
    @Builder.Default
    private Property<Boolean> remove = Property.of(true);

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        try (var client = DockerService.client(runContext, runContext.render(host).as(String.class).orElse(null), config, credentials, null)) {
            if (!runContext.render(kill).as(Boolean.class).orElseThrow()) {
                client.stopContainerCmd(runContext.render(containerId).as(String.class).orElseThrow()).exec();
            } else {
                client.killContainerCmd(runContext.render(containerId).as(String.class).orElseThrow()).exec();
            }

            if (runContext.render(remove).as(Boolean.class).orElseThrow()) {
                client.removeContainerCmd(runContext.render(containerId).as(String.class).orElseThrow()).exec();
            }
        }

        return null;
    }
}
