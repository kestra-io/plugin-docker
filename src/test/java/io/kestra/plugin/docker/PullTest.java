package io.kestra.plugin.docker;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.runner.docker.Credentials;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class PullTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void pullFromPublicRepository() throws Exception {
        final String image = "alpine:latest";

        Pull pull = Pull.builder()
            .id("run")
            .type(Stop.class.getName())
            .image(Property.ofValue(image))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, pull, ImmutableMap.of());

        rmImageIfExists(runContext, image);
        assertThat(imageExists(runContext, image), is(false));

        pull.run(runContext);
        assertThat(imageExists(runContext, image), is(true));
    }

    @Test
    void pullFromPrivateRepository() throws Exception {
        String image = getRegistry() + "/" + getPrivateImage();

        Pull pull = Pull.builder()
            .id("run")
            .type(Stop.class.getName())
            .image(Property.ofValue(image))
            .credentials(Credentials.builder()
                .username(Property.ofValue(getUsername()))
                .password(Property.ofValue(getPassword()))
                .registry(Property.ofValue(getRegistry()))
                .build()
            )
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, pull, ImmutableMap.of());

        rmImageIfExists(runContext, image);
        assertThat(imageExists(runContext, image), is(false));

        pull.run(runContext);
        assertThat(imageExists(runContext, image), is(true));
    }
}
