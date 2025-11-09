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

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
public class PushTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;

    private final AbstractDockerHelper helper = new AbstractDockerHelper();

    @Test
    void pushToLocalRegistryAndPullBack() throws Exception {
        RunContext runContext = runContextFactory.of();

        String localTag = helper.getPrivateImage();
        String registry = helper.getRegistry();
        String remoteTag = registry + "/" + localTag;

        helper.rmImageIfExists(runContext, localTag, null);
        helper.rmImageIfExists(runContext, remoteTag, null);

        helper.buildImage(runContextFactory, localTag, "push-test");

        assertThat(helper.getImageId(runContext, localTag, null), notNullValue());

        Credentials credentials = Credentials.builder()
            .registry(Property.ofValue(registry))
            .username(Property.ofValue(helper.getUsername()))
            .password(Property.ofValue(helper.getPassword()))
            .build();

        Image imageTask = Image.builder()
            .id("tag")
            .type(Image.class.getName())
            .command(Property.ofValue(Image.Command.TAG))
            .sourceImage(Property.ofValue(localTag))
            .targetImage(Property.ofValue(remoteTag))
            .credentials(credentials)
            .build();

        RunContext tagContext = TestsUtils.mockRunContext(runContextFactory, imageTask, ImmutableMap.of());
        imageTask.run(tagContext);

        Push pushTask = Push.builder()
            .id("push")
            .type(Push.class.getName())
            .tags(Property.ofValue(List.of(remoteTag)))
            .credentials(credentials)
            .build();

        RunContext pushContext = TestsUtils.mockRunContext(runContextFactory, pushTask, ImmutableMap.of());
        pushTask.run(pushContext);

        helper.rmImageIfExists(runContext, remoteTag, credentials);
        assertThat(helper.getImageId(runContext, remoteTag, credentials), nullValue());

        Pull pullTask = Pull.builder()
            .id("pull")
            .type(Pull.class.getName())
            .image(Property.ofValue(remoteTag))
            .credentials(credentials)
            .build();

        RunContext pullContext = TestsUtils.mockRunContext(runContextFactory, pullTask, ImmutableMap.of());
        pullTask.run(pullContext);

        assertThat(helper.getImageId(runContext, remoteTag, credentials), notNullValue());
    }
}
