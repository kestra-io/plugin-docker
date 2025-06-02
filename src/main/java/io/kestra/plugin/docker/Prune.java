package io.kestra.plugin.docker;

import com.github.dockerjava.api.command.PruneCmd;
import com.github.dockerjava.api.model.PruneType;
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

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Prune unused containers, images, networks, volumes.",
    description = "Use this task to clean your environment and delete unused containers/images/networks/volumes"
)
@Plugin(
    examples = {
        @Example(
            title = "Prune all docker images",
            full = true,
            code = """
                id: docker_prune_images
                namespace: company.team

                tasks:
                  - id: prune_images
                    type: io.kestra.plugin.docker.Prune
                    pruneType: IMAGES
                    dangling: true
                """
        )
    }
)
public class Prune extends AbstractDocker implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Prune type.",
        description = """
    Type of docker object you want to prune :
        - BUILD
        - CONTAINERS
        - IMAGES
        - NETWORKS
        - VOLUMES
    """
    )
    @NotNull
    Property<PruneType> pruneType;

    @Schema(
        title = "Dangling.",
        description = """
            When set to true, prune only unused and untagged images.
            When set to false, all unused images are pruned. Meaningful only for IMAGES prune type
            """
    )
    @Builder.Default
    Property<Boolean> dangling = Property.ofValue(Boolean.FALSE);

    @Schema(
        title = "Until filter.",
        description = """
            Prune containers created before this timestamp Meaningful only for CONTAINERS and IMAGES prune type
            Can be Unix timestamps, date formatted timestamps, or Go duration strings (e. g. 10m, 1h30m) computed relative to the daemon machine’s time.
            """
    )
    Property<String> until;

    @Schema(
        title = "Label filters.",
        description = "Prune containers with the specified labels."
    )
    Property<List<String>> labelFilters;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        try (var client = DockerService.client(runContext, runContext.render(host).as(String.class).orElse(null), config, credentials, null)) {
            PruneCmd pruneCmd = client.pruneCmd(runContext.render(this.pruneType).as(PruneType.class).orElseThrow());

            runContext.render(this.dangling).as(Boolean.class).ifPresent(pruneCmd::withDangling);
            runContext.render(this.until).as(String.class).ifPresent(pruneCmd::withUntilFilter);

            List<String> renderedLabelFilters =  runContext.render(this.labelFilters).asList(String.class);
            pruneCmd.withLabelFilter(renderedLabelFilters.toArray(new String[0]));

            pruneCmd.exec();
        }
        return null;
    }
}
