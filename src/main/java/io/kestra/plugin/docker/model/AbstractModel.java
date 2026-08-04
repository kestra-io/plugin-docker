package io.kestra.plugin.docker.model;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractModel extends Task {

    private static final String DEFAULT_HOST = "http://localhost:12434";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Schema(
        title = "Docker Model Runner host",
        description = "Base URL of the Docker Model Runner REST API. Override when DMR is exposed on a non-default address."
    )
    @PluginProperty(group = "connection")
    @lombok.Builder.Default
    protected Property<String> host = Property.ofValue(DEFAULT_HOST);

    protected String resolvedHost(RunContext runContext) throws IllegalVariableEvaluationException {
        var rHost = runContext.render(this.host).as(String.class).orElse(DEFAULT_HOST);
        return rHost.endsWith("/") ? rHost.substring(0, rHost.length() - 1) : rHost;
    }

    /**
     * A Kestra HTTP client for the Docker Model Runner REST API. Using the shared client gives
     * timeouts, proxy support, allowed-host controls and non-2xx handling consistent with the other
     * plugins, rather than a bare JDK client. Callers own the lifecycle (try-with-resources).
     */
    protected HttpClient httpClient(RunContext runContext) throws IllegalVariableEvaluationException {
        var configuration = HttpConfiguration.builder()
            .timeout(TimeoutConfiguration.builder()
                .connectTimeout(Property.ofValue(CONNECT_TIMEOUT))
                .build())
            .build();

        return new HttpClient(runContext, configuration);
    }
}
