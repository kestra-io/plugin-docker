package io.kestra.plugin.docker;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.plugin.scripts.runner.docker.Credentials;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractDocker extends Task {

    private static final Set<String> DOCKER_HUB_HOSTS = Set.of(
        "registry-1.docker.io",
        "index.docker.io",
        "docker.io"
    );

    /**
     * Extracts the bare hostname from a registry URL, stripping any scheme and path.
     * Returns {@code null} for Docker Hub registries (no prepending needed).
     */
    static String registryHostForImagePrefix(String registry) {
        if (registry == null || registry.isBlank()) {
            return null;
        }

        String host;
        if (registry.contains("://")) {
            // Has a scheme — parse as URI to extract the host (+port)
            var uri = URI.create(registry);
            host = uri.getHost();
            if (uri.getPort() > 0) {
                host = host + ":" + uri.getPort();
            }
        } else {
            // No scheme — strip any trailing path segments
            var slashIdx = registry.indexOf('/');
            host = slashIdx >= 0 ? registry.substring(0, slashIdx) : registry;
        }

        if (host == null || DOCKER_HUB_HOSTS.contains(host)) {
            return null;
        }

        return host;
    }
    @Schema(
        title = "The URI of your Docker host e.g. localhost"
    )
    @PluginProperty(group = "connection")
    protected Property<String> host;

    @Schema(
        title = "Docker configuration file.",
        description = "Docker configuration file that can set access credentials to private container registries. Usually located in `~/.docker/config.json`.",
        anyOf = { String.class, Map.class }
    )
    @PluginProperty(dynamic = true, group = "advanced")
    protected Object config;

    @Schema(
        title = "Credentials for a private container registry."
    )
    @PluginProperty(dynamic = true, group = "connection")
    protected Credentials credentials;
}
