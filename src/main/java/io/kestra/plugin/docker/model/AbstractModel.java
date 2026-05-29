package io.kestra.plugin.docker.model;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractModel extends Task {

    @Schema(
        title = "Docker Model Runner host",
        description = "Base URL of the Docker Model Runner REST API. Override when DMR is exposed on a non-default address."
    )
    @PluginProperty(group = "connection")
    @lombok.Builder.Default
    protected Property<String> host = Property.ofValue("http://localhost:12434");

    protected String resolvedHost(RunContext runContext) throws IllegalVariableEvaluationException {
        var rHost = runContext.render(this.host).as(String.class).orElse("http://localhost:12434");
        return rHost.endsWith("/") ? rHost.substring(0, rHost.length() - 1) : rHost;
    }
}
