package io.kestra.plugin.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class AbstractDockerTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://registry-1.docker.io/v2/",
        "https://registry-1.docker.io",
        "https://index.docker.io/v1/",
        "https://docker.io",
        "registry-1.docker.io",
        "index.docker.io/v1/",
        "docker.io"
    })
    void registryHostForImagePrefix_returnsNull_forDockerHubRegistries(String registry) {
        assertThat(AbstractDocker.registryHostForImagePrefix(registry), is(nullValue()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void registryHostForImagePrefix_returnsNull_forNullOrBlank(String registry) {
        assertThat(AbstractDocker.registryHostForImagePrefix(registry), is(nullValue()));
    }

    @Test
    void registryHostForImagePrefix_returnsHost_forPrivateRegistryWithScheme() {
        assertThat(AbstractDocker.registryHostForImagePrefix("https://ghcr.io"), is("ghcr.io"));
        assertThat(AbstractDocker.registryHostForImagePrefix("https://ghcr.io/v2/"), is("ghcr.io"));
        assertThat(AbstractDocker.registryHostForImagePrefix("http://myregistry.example.com/v1/"), is("myregistry.example.com"));
    }

    @Test
    void registryHostForImagePrefix_returnsHost_forPrivateRegistryWithoutScheme() {
        assertThat(AbstractDocker.registryHostForImagePrefix("ghcr.io"), is("ghcr.io"));
        assertThat(AbstractDocker.registryHostForImagePrefix("myregistry.example.com/v2/"), is("myregistry.example.com"));
    }

    @Test
    void registryHostForImagePrefix_preservesPort() {
        assertThat(AbstractDocker.registryHostForImagePrefix("https://localhost:5000"), is("localhost:5000"));
        assertThat(AbstractDocker.registryHostForImagePrefix("https://localhost:5000/v2/"), is("localhost:5000"));
        assertThat(AbstractDocker.registryHostForImagePrefix("localhost:5000"), is("localhost:5000"));
    }
}
