package io.kestra.plugin.docker;

import com.github.dockerjava.api.command.PullImageResultCallback;
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

import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Pull a docker image"
)
@Plugin(
    examples = {
        @Example(
            title = "Pull a docker image",
            full = true,
            code = """
                id: docker_pull
                namespace: company.team

                tasks:
                  - id: pul
                    type: io.kestra.plugin.docker.Pull
                    image: alpine:latest
                """
        )
    }
)
public class Pull extends AbstractDocker implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Docker image to use."
    )
    @NotNull
    protected Property<String> image;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String image = runContext.render(this.image).as(String.class).orElseThrow();
        String registry = Optional.ofNullable(this.getCredentials())
            .map(throwFunction(cred -> runContext.render(cred.getRegistry()).as(String.class).orElse(null)))
            .orElse(null);

        if (registry != null && !image.startsWith(registry)) {
            image = String.join("/", registry, image);
        }

        try (var client = DockerService.client(runContext, runContext.render(host).as(String.class).orElse(null), config, credentials, image)) {
            client.pullImageCmd(image).exec(new PullImageResultCallback()).awaitCompletion();
        }
        runContext.logger().info("Successfully pulled image {}", image);
        return null;
    }
}
