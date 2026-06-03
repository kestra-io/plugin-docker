package io.kestra.plugin.docker;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ImageLsTest extends AbstractDockerHelper {

    private static final String TEST_IMAGE = "alpine:latest";

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void listImages_returnsAtLeastOneImage() throws Exception {
        // Ensure the test image is present on the daemon before listing.
        var pull = Pull.builder()
            .id("pull")
            .type(Pull.class.getName())
            .image(Property.ofValue(TEST_IMAGE))
            .build();
        var pullContext = TestsUtils.mockRunContext(runContextFactory, pull, ImmutableMap.of());
        pull.run(pullContext);

        var task = ImageLs.builder()
            .id("image-ls")
            .type(ImageLs.class.getName())
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getImages(), not(empty()));
        assertThat(output.getCount(), greaterThan(0));

        // Every entry must carry a non-blank ID.
        output.getImages().forEach(image ->
            assertThat(image.getId(), not(emptyOrNullString()))
        );
    }

    @Test
    void listImages_imageNameFilter_returnsOnlyMatchingImages() throws Exception {
        // Pull two distinct images so the daemon has more than alpine.
        for (var img : new String[]{TEST_IMAGE, "busybox:latest"}) {
            var pull = Pull.builder()
                .id("pull-" + img.replace(":", "-"))
                .type(Pull.class.getName())
                .image(Property.ofValue(img))
                .build();
            pull.run(TestsUtils.mockRunContext(runContextFactory, pull, ImmutableMap.of()));
        }

        var filtered = ImageLs.builder()
            .id("image-ls-filter")
            .type(ImageLs.class.getName())
            .imageNameFilter(Property.ofValue("alpine"))
            .build();
        var filteredOutput = filtered.run(TestsUtils.mockRunContext(runContextFactory, filtered, ImmutableMap.of()));

        assertThat(filteredOutput.getImages(), not(empty()));

        // Every returned image must reference "alpine"; busybox must be absent.
        filteredOutput.getImages().forEach(image ->
            assertThat(
                "image " + image.getRepoTags() + " should match alpine",
                image.getRepoTags().stream().anyMatch(t -> t.contains("alpine")),
                is(true)
            )
        );
        var hasBusybox = filteredOutput.getImages().stream()
            .anyMatch(image -> image.getRepoTags().stream().anyMatch(t -> t.contains("busybox")));
        assertThat("busybox must not appear when filtering for alpine", hasBusybox, is(false));
    }

    @Test
    void listImages_labelFilter_restrictsResults() throws Exception {
        var build = Build.builder()
            .id("build")
            .type(Build.class.getName())
            .tags(Property.ofValue(java.util.List.of("image-ls-label-test:unit")))
            .labels(Property.ofValue(Map.of("kestra-test", "image-ls")))
            .dockerfile(Property.ofValue("FROM alpine:latest\n"))
            .build();
        var buildContext = runContextFactory.of();
        build.run(buildContext);

        var task = ImageLs.builder()
            .id("image-ls-label")
            .type(ImageLs.class.getName())
            .labelFilter(Property.ofValue(Map.of("kestra-test", "image-ls")))
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        var output = task.run(runContext);

        assertThat(output.getImages(), not(empty()));
        output.getImages().forEach(image ->
            assertThat(image.getLabels(), hasEntry("kestra-test", "image-ls"))
        );
    }

    @Test
    void listImages_outputPopulatesFields() throws Exception {
        var pull = Pull.builder()
            .id("pull")
            .type(Pull.class.getName())
            .image(Property.ofValue(TEST_IMAGE))
            .build();
        var pullContext = TestsUtils.mockRunContext(runContextFactory, pull, ImmutableMap.of());
        pull.run(pullContext);

        var task = ImageLs.builder()
            .id("image-ls-fields")
            .type(ImageLs.class.getName())
            .imageNameFilter(Property.ofValue(TEST_IMAGE))
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        var output = task.run(runContext);

        assertThat(output.getImages(), not(empty()));
        var entry = output.getImages().getFirst();
        assertThat(entry.getId(), startsWith("sha256:"));
        assertThat(entry.getRepoTags(), not(empty()));
        assertThat(entry.getSize(), greaterThan(0L));
        assertThat(entry.getCreated(), greaterThan(0L));
        assertThat(output.getCount(), equalTo(output.getImages().size()));
    }
}
