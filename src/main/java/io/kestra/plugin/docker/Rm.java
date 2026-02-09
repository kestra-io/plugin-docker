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

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Remove Docker containers or images",
    description = "Deletes containers and/or images using the Docker daemon. Supports force deletion and removing attached volumes; defaults to safe (force=false, removeVolumes=false)."
)
@Plugin(
    examples = {
        @Example(
            title = "Remove multiple docker containers",
            full = true,
            code = """
                id: docker_remove_containers
                namespace: company.team

                tasks:
                  - id: remove_containers
                    type: io.kestra.plugin.docker.Rm
                    force: true
                    containerIds:
                        - 947795c71c71
                        - 5ad36bff753e
                """
        )
    }
)
public class Rm extends AbstractDocker implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Container IDs",
        description = "List of container IDs to remove."
    )
    protected Property<List<String>> containerIds;

    @Schema(
        title = "Image IDs",
        description = "List of image IDs to remove."
    )
    protected Property<List<String>> imageIds;

    @Schema(
        title = "Remove volumes",
        description = "When true, also deletes volumes attached to containers being removed."
    )
    @Builder.Default
    protected Property<Boolean> removeVolumes = Property.ofValue(Boolean.FALSE);

    @Schema(
        title = "Force removal",
        description = "Uses `--force` to delete running containers or referenced images."
    )
    @Builder.Default
    protected Property<Boolean> force = Property.ofValue(Boolean.FALSE);

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        try (var client = DockerService.client(runContext, runContext.render(host).as(String.class).orElse(null), config, credentials, null)) {
            for (String containerId : runContext.render(this.containerIds).asList(String.class)) {
                client.removeContainerCmd(containerId)
                    .withForce(runContext.render(this.force).as(Boolean.class).orElseThrow())
                    .withRemoveVolumes(runContext.render(this.removeVolumes).as(Boolean.class).orElseThrow())
                    .exec();
            }

            for (String imageId : runContext.render(this.imageIds).asList(String.class)) {
                client.removeImageCmd(imageId)
                    .withForce(runContext.render(this.force).as(Boolean.class).orElseThrow())
                    .exec();
            }
        }
        return null;
    }
}
