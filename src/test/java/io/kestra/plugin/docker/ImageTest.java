package io.kestra.plugin.docker;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
class ImageTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void tagImage() throws Exception {
        Build build = Build.builder()
            .id("build")
            .type(Build.class.getName())
            .platforms(Property.ofValue(List.of("linux/amd64")))
            .buildArgs(Property.ofValue(Map.of("APT_PACKAGES", "curl")))
            .labels(Property.ofValue(Map.of("unit-test", "true")))
            .tags(Property.ofValue(List.of("image-source:unit")))
            .dockerfile(Property.ofValue("""
                FROM ubuntu
                ARG APT_PACKAGES=""
                RUN apt-get update && apt-get install -y --no-install-recommends ${APT_PACKAGES};
            """))
            .build();

        RunContext buildContext = TestsUtils.mockRunContext(runContextFactory, build, ImmutableMap.of());
        Build.Output output = build.run(buildContext);
        assertThat(output.getImageId(), notNullValue());

        Image imageTask = Image.builder()
            .id("tag")
            .type(Image.class.getName())
            .command(Property.ofValue(Image.Command.TAG))
            .sourceImage(Property.ofValue("image-source:unit"))
            .targetImage(Property.ofValue("image-target:unit"))
            .build();

        RunContext tagContext = TestsUtils.mockRunContext(runContextFactory, imageTask, ImmutableMap.of());
        imageTask.run(tagContext);
    }
}
