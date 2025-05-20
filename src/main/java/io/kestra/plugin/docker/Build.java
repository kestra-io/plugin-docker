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
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Rethrow;
import io.kestra.plugin.scripts.runner.docker.Credentials;
import io.kestra.plugin.scripts.runner.docker.DockerService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Build a Docker image and optionally push it to a remote container registry."
)
@Plugin(
    examples = {
        @Example(
            title = "Build and push a Docker image to a registry",
            full = true,
            code = """
                id: docker_build
                namespace: company.team

                tasks:
                  - id: build
                    type: io.kestra.plugin.docker.Build
                    push: true
                    dockerfile: |
                      FROM ubuntu
                      ARG APT_PACKAGES=""
                      RUN apt-get update && apt-get install -y --no-install-recommends ${APT_PACKAGES};
                    platforms:
                      - linux/amd64
                    tags:
                      - private-registry.io/unit-test:latest
                    buildArgs:
                      APT_PACKAGES: curl
                    labels:
                      unit-test: "true"
                    credentials:
                      registry: <registry.url.com>
                      username: "{{ secret('DOCKERHUB_USERNAME') }}"
                      password: "{{ secret('DOCKERHUB_PASSWORD') }}"
                """
        ),
        @Example(
            full = true,
            title = "Build and push a docker image to DockerHub",
            code = """
                id: build_dockerhub_image
                namespace: company.team
                
                tasks:
                  - id: build
                    type: io.kestra.plugin.docker.Build
                    dockerfile: |
                      FROM python:3.10
                      RUN pip install --upgrade pip
                      RUN pip install --no-cache-dir kestra requests "polars[all]"
                    tags:
                      - kestra/polars:latest
                    push: true
                    credentials:
                      registry: https://index.docker.io/v1/ # for now only V1 is supported until https://github.com/kestra-io/plugin-docker/issues/66
                      username: "{{ secret('DOCKERHUB_USERNAME') }}"
                      password: "{{ secret('DOCKERHUB_PASSWORD') }}"
                """
        ),
        @Example(
            full = true,
            title = "Build a Docker image and push it to GitHub Container Registry (ghcr.io)",
            code = """
                id: build_github_container_image
                namespace: company.team
                
                tasks:
                  - id: build
                    type: io.kestra.plugin.docker.Build
                    dockerfile: |
                      FROM python:3.10
                      RUN pip install --upgrade pip
                      RUN pip install --no-cache-dir kestra requests "polars[all]"
                    tags:
                      - ghcr.io/kestra/polars:latest
                    push: true
                    credentials:
                      username: kestra
                      password: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                """
        ),
        @Example(
            full = true,
            title = "Build a Docker image and use it with Python script using a Docker Task Runner",
            code = """
                id: build_task_runner_image
                namespace: company.team
                
                tasks:
                  - id: build
                    type: io.kestra.plugin.docker.Build
                    tags:
                      - my-py-data-app
                    dockerfile: |
                      FROM python:3.12-slim
                      WORKDIR /app
                      RUN pip install --no-cache-dir pandas
                      COPY . /app
                
                  - id: python
                    type: io.kestra.plugin.scripts.python.Commands
                    containerImage: "{{ outputs.build.imageId }}"
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                      pullPolicy: NEVER
                    namespaceFiles:
                      enabled: true
                    commands:
                      - python main.py
                """
        )
    }
)
public class Build extends AbstractDocker implements RunnableTask<Build.Output>, NamespaceFilesInterface, InputFilesInterface {
    @Schema(
        title = "The contents of your Dockerfile passed as a string, or a path to the Dockerfile"
    )
    private Property<String> dockerfile;

    @Schema(
        title = "The target platform for the image e.g. linux/amd64."
    )
    private Property<List<String>> platforms;

    @Schema(
        title = "Whether to push the image to a remote container registry."
    )
    @Builder.Default
    private Property<Boolean> push = Property.of(false);

    @Schema(
        title = "Always attempt to pull the latest version of the base image."
    )
    @Builder.Default
    private Property<Boolean> pull = Property.of(true);

    @Schema(
        title = "The list of tag of this image.",
        description = "If pushing to a custom registry, the tag should include the registry URL. " +
            "Note that if you want to push to an insecure registry (HTTP), you need to edit the `/etc/docker/daemon.json` file on your Kestra host to [this](https://gist.github.com/brian-mulier-p/0c5a0ae85e83a179d6e93b22cb471934) and restart docker service (`sudo systemctl daemon-reload && sudo systemctl restart docker`)."
    )
    @NotNull
    private Property<List<String>> tags;

    @Schema(
        title = "Optional build arguments in a `key: value` format."
    )
    protected Property<Map<String, String>> buildArgs;

    @Schema(
        title = "Additional metadata for the image in a `key: value` format."
    )
    protected Property<Map<String, String>> labels;

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> renderedTags = runContext.render(this.tags).asList(String.class).isEmpty() ? new ArrayList<>() :  runContext.render(this.tags).asList(String.class);
        Set<String> tags = renderedTags.stream().map(this::removeScheme).collect(Collectors.toSet());

        if (this.namespaceFiles != null && Boolean.TRUE.equals(runContext.render(this.namespaceFiles.getEnabled()).as(Boolean.class).orElse(true))) {
            runContext.storage()
                .namespace()
                .findAllFilesMatching(
                    runContext.render(this.namespaceFiles.getInclude()).asList(String.class),
                    runContext.render(this.namespaceFiles.getExclude()).asList(String.class)
                )
                .forEach(Rethrow.throwConsumer(namespaceFile -> {
                    InputStream content = runContext.storage().getFile(namespaceFile.uri());
                    runContext.workingDir().putFile(Path.of(namespaceFile.path()), content);
                }));
        }

        if (this.inputFiles != null) {
            FilesService.inputFiles(runContext, this.inputFiles);
        }

        try (
            DockerClient dockerClient = DockerService.client(
            runContext,
            runContext.render(this.host).as(String.class).orElse(null),
            this.getConfig(),
            this.getCredentials(),
            tags.iterator().next()
        )) {
            BuildImageCmd buildImageCmd = dockerClient.buildImageCmd()
                .withPull(runContext.render(this.pull).as(Boolean.class).orElseThrow());

            Path path = runContext.workingDir().path();
            String dockerfile = runContext.render(this.dockerfile).as(String.class).orElseThrow();
            Path dockerFile;

            if (path.resolve(dockerfile).toFile().exists()) {
                dockerFile = runContext.workingDir().resolve(Path.of(dockerfile));
            } else {
                dockerFile = runContext.workingDir().createTempFile(dockerfile.getBytes(StandardCharsets.UTF_8), ".dockerfile");
            }

            buildImageCmd.withDockerfile(dockerFile.toFile());

            List<String> renderedPlatforms = runContext.render(platforms).asList(String.class);
            if (!renderedPlatforms.isEmpty()) {
                renderedPlatforms.forEach(buildImageCmd::withPlatform);
            }

            buildImageCmd.withTags(tags);

            var renderedArgs = runContext.render(this.buildArgs).asMap(String.class, String.class);
            if (!renderedArgs.isEmpty()) {
                renderedArgs.forEach(buildImageCmd::withBuildArg);
            }

            var renderedLabel = runContext.render(this.labels).asMap(String.class, String.class);
            if (!renderedLabel.isEmpty()) {
                buildImageCmd.withLabels(renderedLabel);
            }

            String imageId = buildImageCmd
                .exec(new BuildImageResultCallback(runContext))
                .awaitImageId();

            if (runContext.render(this.push).as(Boolean.class).orElseThrow()) {
                for (String tag : tags) {
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

    private String removeScheme(String string) {
        return string.contains("://") ? string.split("://")[1] : string;
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
    public static class PushResponseItemCallback extends ResultCallback.Adapter<PushResponseItem> {
        private final RunContext runContext;
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
        private final RunContext runContext;

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
