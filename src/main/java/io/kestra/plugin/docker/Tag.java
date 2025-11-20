package io.kestra.plugin.docker;

import com.github.dockerjava.api.DockerClient;
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
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Tag a Docker image."
)
@Plugin(
    examples = {
        @Example(
            title = "Tag an existing Docker image",
            full = true,
            code = """
                id: tag_image
                namespace: company.team

                tasks:
                  - id: tag
                    type: io.kestra.plugin.docker.Tag
                    sourceImage: my-app:build-123
                    targetImage: my-registry.example.com/prod/my-app:1.0.0
                """
        )
    }
)
public class Tag extends AbstractDocker implements RunnableTask<VoidOutput> {

    @Schema(title = "Source image name or ID.")
    @NotNull
    private Property<String> sourceImage;

    @Schema(title = "Target image name.")
    @NotNull
    private Property<String> targetImage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rSourceImage = runContext.render(sourceImage).as(String.class).orElseThrow();
        var rTargetImage = runContext.render(targetImage).as(String.class).orElseThrow();
        var rHost = runContext.render(this.host).as(String.class).orElse(null);

        try (DockerClient client = DockerService.client(runContext, rHost, this.getConfig(), this.getCredentials(), rSourceImage)) {
            String repository;
            String tag;

            int lastColon = rTargetImage.lastIndexOf(':');
            if (lastColon > rTargetImage.lastIndexOf('/')) {
                repository = rTargetImage.substring(0, lastColon);
                tag = rTargetImage.substring(lastColon + 1);
            } else {
                repository = rTargetImage;
                tag = "latest";
            }

            runContext.logger().info("Tagging image {} as {}:{}", rSourceImage, repository, tag);

            client.tagImageCmd(rSourceImage, repository, tag).exec();
        }

        return null;
    }
}
