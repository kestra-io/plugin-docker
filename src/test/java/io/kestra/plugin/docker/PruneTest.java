package io.kestra.plugin.docker;

import com.github.dockerjava.api.model.PruneType;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class PruneTest extends AbstractDockerHelper {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void buildAndPruneImages() throws Exception {
        final String image1 = "unit-test-image1:1.2.3";
        final String label1 = "label-1";

        final String image2 = "unit-test-image2:1.2.3";
        final String label2 = "label-2";

        buildImage(runContextFactory, image1,label1);
        buildImage(runContextFactory, image2, label2);

        assertThat(imageExists(runContextFactory.of(), image1), is(true));
        assertThat(imageExists(runContextFactory.of(), image2), is(true));

        Prune prune = Prune.builder()
            .id(PruneTest.class.getSimpleName())
            .type(PruneTest.class.getName())
            .pruneType(Property.of(PruneType.IMAGES))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, prune, Map.of());

        prune.run(runContext);

        assertThat(imageExists(runContextFactory.of(), image1), is(false));
        assertThat(imageExists(runContextFactory.of(), image2), is(false));
    }
}
