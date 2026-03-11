package io.kestra.plugin.docker;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
class TagTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void tagImage() throws Exception {
        var runContext = runContextFactory.of();

        var build = Build.builder()
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

        var output = build.run(runContext);
        assertThat(output.getImageId(), notNullValue());

        var tagTask = Tag.builder()
            .id("tag")
            .type(Tag.class.getName())
            .sourceImage(Property.ofValue("image-source:unit"))
            .targetImage(Property.ofValue("image-target:unit"))
            .build();

        tagTask.run(runContext);
    }
}
