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
    title = "Manage Docker images (tag, remove, etc)."
)
@Plugin(
    examples = {
        @Example(
            title = "Tag an existing image with a new name",
            full = true,
            code = """
                id: docker_image_tag
                namespace: company.team

                tasks:
                  - id: tag
                    type: io.kestra.plugin.docker.Image
                    command: TAG
                    sourceImage: my-app:build-123
                    targetImage: my-registry.example.com/prod/my-app:1.0.0
                """
        ),
        @Example(
            title = "Remove an image",
            full = true,
            code = """
                id: docker_image_rm
                namespace: company.team

                tasks:
                  - id: rm
                    type: io.kestra.plugin.docker.Image
                    command: REMOVE
                    sourceImage: my-registry.example.com/prod/my-app:1.0.0
                    force: true
                """
        )
    }
)
public class Image extends AbstractDocker implements RunnableTask<VoidOutput> {

    public enum Command {
        TAG,
        REMOVE
    }

    @Schema(
        title = "The image command to execute.",
        description = "Supported commands: TAG, REMOVE."
    )
    @NotNull
    private Property<Command> command;

    @Schema(
        title = "Source image name or ID.",
        description = "For TAG, this is the existing image. For REMOVE, this is the image to remove."
    )
    @NotNull
    private Property<String> sourceImage;

    @Schema(
        title = "Target image name when tagging.",
        description = "Equivalent to the second argument of `docker image tag SOURCE TARGET`."
    )
    private Property<String> targetImage;

    @Schema(
        title = "Force the remove operation (docker image rm --force).",
        defaultValue = "false"
    )
    private Property<Boolean> force;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Command cmd = runContext.render(this.command).as(Command.class).orElseThrow();
        String src = runContext.render(this.sourceImage).as(String.class).orElseThrow();

        String host = runContext.render(this.host).as(String.class).orElse(null);

        try (DockerClient client = DockerService.client(runContext, host, this.getConfig(), this.getCredentials(), src)) {
            switch (cmd) {
                case TAG -> tagImage(runContext, client, src);
                case REMOVE -> removeImage(runContext, client, src);
                default -> throw new IllegalArgumentException("Unsupported command: " + cmd);
            }
        }

        return null;
    }

    private void tagImage(RunContext runContext, DockerClient client, String source) throws Exception {
        String target = runContext.render(this.targetImage).as(String.class).orElse(null);
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("targetImage must be provided for TAG command");
        }

        String repository;
        String tag;

        int lastColon = target.lastIndexOf(':');
        if (lastColon > target.lastIndexOf('/')) {
            repository = target.substring(0, lastColon);
            tag = target.substring(lastColon + 1);
        } else {
            repository = target;
            tag = "latest";
        }

        runContext.logger().info("Tagging image {} as {}:{}", source, repository, tag);
        client.tagImageCmd(source, repository, tag).exec();
    }

    private void removeImage(RunContext runContext, DockerClient client, String source) throws Exception {
        boolean forceValue = runContext.render(this.force).as(Boolean.class).orElse(false);

        runContext.logger().info("Removing image {} (force={})", source, forceValue);
        client.removeImageCmd(source)
            .withForce(forceValue)
            .exec();
    }
}
