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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Push Docker images to a registry",
    description = "Pushes existing local images by tag to a remote registry using the available Docker daemon and optional registry credentials. Tags must already exist locally."
)
@Plugin(
    examples = {
        @Example(
            title = "Push a previously built image to DockerHub",
            full = true,
            code = """
                id: docker_push
                namespace: company.team

                tasks:
                  - id: push
                    type: io.kestra.plugin.docker.Push
                    tags:
                      - image/demo:latest
                    credentials:
                      registry: https://index.docker.io/v1/
                      username: "{{ secret('DOCKERHUB_USERNAME') }}"
                      password: "{{ secret('DOCKERHUB_PASSWORD') }}"
                """
        ),
        @Example(
            title = "Push the image from a previous Build task",
            full = true,
            code = """
                id: docker_push_with_tag
                namespace: company.team

                tasks:
                  - id: build
                    type: io.kestra.plugin.docker.Build
                    dockerfile: |
                      FROM alpine
                      RUN echo "hello"
                    tags:
                      - my-registry.example.com/my-app:latest

                  - id: push
                    type: io.kestra.plugin.docker.Push
                    tags:
                      - my-registry.example.com/my-app:latest
                    credentials:
                      registry: my-registry.example.com
                      username: "{{ secret('REGISTRY_USERNAME') }}"
                      password: "{{ secret('REGISTRY_PASSWORD') }}"
                """
        )
    }
)
public class Push extends AbstractDocker implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Image tags to push",
        description = "Provide one or more tags; use fully qualified references for custom registries."
    )
    @NotNull
    private Property<List<String>> tags;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rTags = runContext.render(this.tags).asList(String.class);
        if (rTags == null || rTags.isEmpty()) {
            throw new IllegalArgumentException("At least one tag must be provided");
        }

        Set<String> tagsWithoutScheme = rTags.stream()
            .map(this::removeScheme)
            .collect(Collectors.toSet());

        try (var dockerClient = DockerService.client(
            runContext,
            runContext.render(this.host).as(String.class).orElse(null),
            this.getConfig(),
            this.getCredentials(),
            tagsWithoutScheme.iterator().next()
        )) {
            for (String tag : tagsWithoutScheme) {
                PushResponseItemCallback callback = new PushResponseItemCallback(runContext);
                dockerClient.pushImageCmd(tag)
                    .exec(callback);

                callback.awaitCompletion();

                if (callback.getError() != null) {
                    throw callback.getError();
                }
            }
        }

        return null;
    }

    private String removeScheme(String string) {
        return string.contains("://") ? string.split("://", 2)[1] : string;
    }
}
