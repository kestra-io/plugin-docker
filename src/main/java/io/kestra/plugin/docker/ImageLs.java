package io.kestra.plugin.docker;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.command.ListImagesCmd;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
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
    title = "List Docker images on the host",
    description = """
        Lists images available on the Docker daemon and returns their IDs, repo tags, digests, and sizes.
        Useful for capturing an image ID before and after a pull to detect whether the image changed.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List all Docker images on the host",
            full = true,
            code = """
                id: docker_image_ls
                namespace: company.team

                tasks:
                  - id: list_images
                    type: io.kestra.plugin.docker.ImageLs
                """
        ),
        @Example(
            title = "Filter images by name",
            full = true,
            code = """
                id: docker_image_ls_filter
                namespace: company.team

                tasks:
                  - id: list_alpine
                    type: io.kestra.plugin.docker.ImageLs
                    imageNameFilter: alpine
                """
        ),
        @Example(
            title = "Detect whether a pull changed the local image ID",
            full = true,
            code = """
                id: docker_detect_image_change
                namespace: company.team

                tasks:
                  - id: before
                    type: io.kestra.plugin.docker.ImageLs
                    imageNameFilter: alpine:latest

                  - id: pull
                    type: io.kestra.plugin.docker.Pull
                    image: alpine:latest

                  - id: after
                    type: io.kestra.plugin.docker.ImageLs
                    imageNameFilter: alpine:latest

                  - id: check
                    type: io.kestra.plugin.core.flow.If
                    condition: "{{ (outputs.before.images[0].id) != (outputs.after.images[0].id) }}"
                    then:
                      - id: updated
                        type: io.kestra.plugin.core.log.Log
                        message: "Image was updated"
                    else:
                      - id: unchanged
                        type: io.kestra.plugin.core.log.Log
                        message: "Image is up to date"
                """
        )
    }
)
public class ImageLs extends AbstractDocker implements RunnableTask<ImageLs.Output> {

    @Schema(
        title = "Include intermediate and dangling images",
        description = "When true, intermediate build layers and untagged (dangling) images are included in the result. Defaults to false."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Boolean> showAll = Property.ofValue(false);

    @Schema(
        title = "Filter images by name or reference",
        description = "Only images whose repository or tag contains this string are returned. Accepts partial names such as `alpine` or full references such as `alpine:3.18`."
    )
    @PluginProperty(group = "processing")
    private Property<String> imageNameFilter;

    @Schema(
        title = "Filter images by label",
        description = "Only images that carry all of the supplied labels are returned. Each entry is a key/value pair, e.g. `{\"env\": \"prod\"}`."
    )
    @PluginProperty(group = "processing")
    private Property<Map<String, String>> labelFilter;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rHost = runContext.render(host).as(String.class).orElse(null);
        var rShowAll = runContext.render(showAll).as(Boolean.class).orElse(false);
        var rImageNameFilter = runContext.render(imageNameFilter).as(String.class).orElse(null);
        var rLabelFilter = runContext.render(labelFilter).asMap(String.class, String.class);

        List<ImageEntry> images;

        try (var client = DockerService.client(runContext, rHost, config, credentials, null)) {
            ListImagesCmd cmd = client.listImagesCmd().withShowAll(rShowAll);

            if (rImageNameFilter != null) {
                // withReferenceFilter sets filters={"reference":[...]} (Docker API >= 1.25).
                // The legacy withImageNameFilter uses the ?filter= param ignored since API 1.41.
                cmd.withReferenceFilter(rImageNameFilter);
            }

            if (!rLabelFilter.isEmpty()) {
                cmd.withLabelFilter(rLabelFilter);
            }

            images = cmd.exec()
                .stream()
                .map(image -> ImageEntry.builder()
                    .id(image.getId())
                    .repoTags(image.getRepoTags() != null ? Arrays.asList(image.getRepoTags()) : List.of())
                    .repoDigests(image.getRepoDigests() != null ? Arrays.asList(image.getRepoDigests()) : List.of())
                    .size(image.getSize())
                    .created(image.getCreated())
                    .labels(image.getLabels() != null ? image.getLabels() : Map.of())
                    .build())
                .toList();
        }

        runContext.logger().info("Found {} image(s)", images.size());

        return Output.builder()
            .images(images)
            .count(images.size())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Listed images")
        private List<ImageEntry> images;

        @Schema(title = "Number of images returned")
        private int count;
    }

    @Builder
    @Getter
    @Schema(title = "A single image entry returned by ImageLs")
    public static class ImageEntry {
        @Schema(title = "Image ID", description = "Full image ID including the `sha256:` prefix.")
        private String id;

        @Schema(title = "Repository tags", description = "Tags associated with the image, e.g. `alpine:3.18`.")
        private List<String> repoTags;

        @Schema(title = "Repository digests", description = "Content-addressable digests for the image, e.g. `alpine@sha256:...`.")
        private List<String> repoDigests;

        @Schema(title = "Image size in bytes")
        private Long size;

        @Schema(title = "Creation timestamp", description = "Unix epoch seconds at which the image was created.")
        private Long created;

        @Schema(title = "Image labels")
        private Map<String, String> labels;
    }
}
