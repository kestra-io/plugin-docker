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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class RmTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void runAndRemoveContainersAndImages() throws Exception {
        final String image = "redis:6.2.17-alpine";

        String containerId1 = runContainer(runContextFactory, image);
        String containerId2 = runContainer(runContextFactory, image);

        assertThat(containerExists(containerId1, runContextFactory.of()), is(true));
        assertThat(containerExists(containerId2, runContextFactory.of()), is(true));
        assertThat(imageExists(runContextFactory.of(), image), is(true));

        //Force remove containers
        Rm removeContainers = Rm.builder()
            .id(Run.class.getSimpleName())
            .type(Rm.class.getName())
            .containerIds(Property.ofValue(List.of(containerId1, containerId2)))
            .force(Property.ofValue(true))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, removeContainers, ImmutableMap.of());

        removeContainers.run(runContext);

        assertThat(containerExists(containerId1, runContextFactory.of()), is(false));
        assertThat(containerExists(containerId2, runContextFactory.of()), is(false));

        assertThat(imageExists(runContextFactory.of(), image), is(true));

        //Force remove containers
        Rm removeImages = Rm.builder()
            .id(Run.class.getSimpleName())
            .type(Rm.class.getName())
            .imageIds(Property.ofValue(List.of(image)))
            .build();

        removeImages.run(runContext);
        assertThat(imageExists(runContextFactory.of(), image), is(false));
    }
}
