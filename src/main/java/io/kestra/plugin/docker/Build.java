package io.kestra.plugin.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.runners.DockerService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Build a Docker image and push it to a remote container registry."
)
@Plugin(
    examples = {
        @Example(
            title = "Build and push a Docker image to a registry",
            code = {"dockerfile: |",
                "  FROM ubuntu",
                "  ARG APT_PACKAGES=\"\"",
                "",
                "  RUN apt-get update && apt-get install -y --no-install-recommends ${APT_PACKAGES};",
                "platforms:",
                "- linux/amd64",
                "tag: private-registry.io/unit-test:latest",
                "buildArgs:",
                "  APT_PACKAGES: curl",
                "labels:",
                "  unit-test: \"true\"",
                "credentials:",
                "  username: <your-user>",
                "  password: <your-password>",
            }
        ),
    }
)
public class Build extends Task implements RunnableTask<Build.Output> {
    @Schema(
        title = "The URI of your Docker host e.g. localhost"
    )
    @PluginProperty(dynamic = true)
    private String host;

    @Schema(
        title = "Credentials to push your image to a container registry."
    )
    @PluginProperty
    private DockerOptions.Credentials credentials;

    @Schema(
        title = "The contents of your Dockerfile passed as a string, or a path to the Dockerfile"
    )
    @PluginProperty(dynamic = true)
    private String dockerfile;

    @Schema(
        title = "The target platform for the image e.g. linux/amd64."
    )
    @PluginProperty(dynamic = true)
    private List<String> platforms;

    @Schema(
        title = "Whether to push the image to a remote container registry."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Boolean push = false;

    @Schema(
        title = "Always attempt to pull the latest version of the base image."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Boolean pull = true;

    @Schema(
        title = "The tag of this image."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private String tag;

    @Schema(
        title = "Optional build arguments in a `key: value` format."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> buildArgs;

    @Schema(
        title = "Additional metadata for the image in a `key: value` format."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> labels;

    @Override
    public Output run(RunContext runContext) throws Exception {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(DockerService.findHost(runContext, this.host));

        try (DockerClient dockerClient = DockerService.client(builder.build())) {
            BuildImageCmd buildImageCmd = dockerClient.buildImageCmd()
                .withPull(this.pull);

            Path path = runContext.tempDir();
            String dockerfile = runContext.render(this.dockerfile);
            Path dockerFile;

            if (path.resolve(dockerfile).toFile().exists()) {
                dockerFile = path.resolve(dockerfile);
            } else {
                dockerFile = runContext.tempFile(dockerfile.getBytes(StandardCharsets.UTF_8), ".dockerfile");
            }

            buildImageCmd.withDockerfile(dockerFile.toFile());

            if (this.platforms != null) {
                runContext.render(this.platforms).forEach(buildImageCmd::withPlatform);
            }

            if (this.tag != null) {
                buildImageCmd.withTags(Set.of(runContext.render(this.tag)));
            }

            if (this.buildArgs != null) {
                runContext.renderMap(this.buildArgs).forEach(buildImageCmd::withBuildArg);
            }

            if (this.labels != null) {
                buildImageCmd.withLabels(runContext.renderMap(this.labels));
            }

            String imageId = buildImageCmd
                .exec(new BuildImageResultCallback())
                .awaitImageId();

            if (this.push) {
                AuthConfig authConfig = new AuthConfig()
                    .withRegistryAddress(DockerService.registryUrlFromImage(tag));

                if (this.credentials != null) {
                    if (this.credentials.getRegistry() != null) {
                        authConfig.withRegistryAddress(runContext.render(this.credentials.getRegistry()));
                    }

                    if (this.credentials.getUsername() != null) {
                        authConfig.withUsername(runContext.render(this.credentials.getUsername()));
                    }

                    if (this.credentials.getPassword() != null) {
                        authConfig.withPassword(runContext.render(this.credentials.getPassword()));
                    }

                    if (this.credentials.getAuth() != null) {
                        authConfig.withAuth(runContext.render(this.credentials.getAuth()));
                    }

                    if (this.credentials.getRegistryToken() != null) {
                        authConfig.withRegistrytoken(runContext.render(this.credentials.getRegistryToken()));
                    }

                    if (this.credentials.getIdentityToken() != null) {
                        authConfig.withIdentityToken(runContext.render(this.credentials.getIdentityToken()));
                    }
                }

                ResultCallback.Adapter<PushResponseItem> resultPush = dockerClient.pushImageCmd(Objects.requireNonNull(
                        buildImageCmd.getTags()).iterator().next())
                    .withAuthConfig(authConfig)
                    .exec(new ResultCallback.Adapter<>());

                resultPush.awaitCompletion();
            }

            return Output.builder()
                .imageId(imageId)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The generated image id."
        )
        private String imageId;
    }
}
