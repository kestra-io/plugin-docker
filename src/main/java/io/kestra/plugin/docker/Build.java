package io.kestra.plugin.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.NamespaceFilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.runner.docker.Credentials;
import io.kestra.plugin.scripts.runner.docker.DockerService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jakarta.validation.constraints.NotNull;

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
                "tags:",
                "- private-registry.io/unit-test:latest",
                "buildArgs:",
                "  APT_PACKAGES: curl",
                "labels:",
                "  unit-test: \"true\"",
                "credentials:",
                "  registry: <registry.url.com>",
                "  username: <your-user>",
                "  password: <your-password>",
            }
        ),
    }
)
public class Build extends Task implements RunnableTask<Build.Output>, NamespaceFilesInterface, InputFilesInterface {
    @Schema(
        title = "The URI of your Docker host e.g. localhost"
    )
    @PluginProperty(dynamic = true)
    private String host;

    @Schema(
        title = "Credentials to push your image to a container registry."
    )
    @PluginProperty
    private Credentials credentials;

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
        title = "The list of tag of this image."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Set<String> tags;

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

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    @SuppressWarnings("unchecked")
    @Override
    public Output run(RunContext runContext) throws Exception {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(DockerService.findHost(runContext, this.host));
        Set<String> tags = runContext.render(this.tags);

        if (this.getCredentials() != null) {
            Path config = DockerService.createConfig(
                runContext,
                Map.of(),
                List.of(this.getCredentials()),
                tags.iterator().next()
            );

            builder.withDockerConfig(config.toFile().getAbsolutePath());
        }

        if (this.namespaceFiles != null ) {
            String tenantId = ((Map<String, String>) runContext.getVariables().get("flow")).get("tenantId");
            String namespace = ((Map<String, String>) runContext.getVariables().get("flow")).get("namespace");

            NamespaceFilesService namespaceFilesService = runContext.getApplicationContext().getBean(NamespaceFilesService.class);
            namespaceFilesService.inject(
                runContext,
                tenantId,
                namespace,
                runContext.tempDir(),
                this.namespaceFiles
            );
        }

        if (this.inputFiles != null) {
            FilesService.inputFiles(runContext, this.inputFiles);
        }

        try (DockerClient dockerClient = DockerService.client(builder.build())) {
            BuildImageCmd buildImageCmd = dockerClient.buildImageCmd()
                .withPull(this.pull);

            Path path = runContext.tempDir();
            String dockerfile = runContext.render(this.dockerfile);
            Path dockerFile;

            if (path.resolve(dockerfile).toFile().exists()) {
                dockerFile = runContext.resolve(Path.of(dockerfile));
            } else {
                dockerFile = runContext.tempFile(dockerfile.getBytes(StandardCharsets.UTF_8), ".dockerfile");
            }

            buildImageCmd.withDockerfile(dockerFile.toFile());

            if (this.platforms != null) {
                runContext.render(this.platforms).forEach(buildImageCmd::withPlatform);
            }

            if (this.tags != null) {
                buildImageCmd.withTags(tags);
            }

            if (this.buildArgs != null) {
                runContext.renderMap(this.buildArgs).forEach(buildImageCmd::withBuildArg);
            }

            if (this.labels != null) {
                buildImageCmd.withLabels(runContext.renderMap(this.labels));
            }

            String imageId = buildImageCmd
                .exec(new BuildImageResultCallback(runContext))
                .awaitImageId();

            if (this.push) {
                for(String tag : tags){
                    PushResponseItemCallback resultPush = dockerClient.pushImageCmd(tag)
                        .exec(new PushResponseItemCallback(runContext));

                    resultPush.awaitCompletion();

                    if (resultPush.getError() != null) {
                        throw resultPush.getError();
                    }
                }
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

    @Getter
    public static class PushResponseItemCallback extends ResultCallback.Adapter<PushResponseItem>  {
        private RunContext runContext;
        private Exception error;

        public PushResponseItemCallback(RunContext runContext) {
            super();

            this.runContext = runContext;
        }

        @Override
        public void onNext(PushResponseItem item) {
            super.onNext(item);

            if (item.getErrorDetail() != null) {
                this.error = new Exception(item.getErrorDetail().getMessage());
            }

            //noinspection deprecation
            if (item.getProgress() != null) {
                this.runContext.logger().debug("{} {}", item.getId(), item.getProgress());
            } else if (item.getRawValues().containsKey("status") &&
                !item.getRawValues().get("status").toString().trim().isEmpty()
            ) {
                this.runContext.logger().info("{} {}", item.getId(), item.getRawValues().get("status").toString().trim());
            }

            if (item.getProgressDetail() != null &&
                item.getProgressDetail().getCurrent() != null &&
                Objects.equals(item.getProgressDetail().getCurrent(), item.getProgressDetail().getTotal())
            ) {
                runContext.metric(Counter.of("bytes", item.getProgressDetail().getTotal()));
            }
        }

        @Override
        public void onError(Throwable throwable) {
            super.onError(throwable);

            this.error = new Exception(throwable);
        }
    }

    public static class BuildImageResultCallback extends com.github.dockerjava.api.command.BuildImageResultCallback {
        private RunContext runContext;

        public BuildImageResultCallback(RunContext runContext) {
            super();

            this.runContext = runContext;
        }

        @Override
        public void onNext(BuildResponseItem item) {
            super.onNext(item);

            if (item.getRawValues().containsKey("stream") &&
                !item.getRawValues().get("stream").toString().trim().isEmpty()
            ) {
                this.runContext.logger().info("{}", item.getRawValues().get("stream").toString().trim());
            }
        }

    }
}
