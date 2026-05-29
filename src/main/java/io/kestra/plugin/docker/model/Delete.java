package io.kestra.plugin.docker.model;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete a model from Docker Model Runner",
    description = """
        Removes a locally available model via the Docker Model Runner (DMR) REST API.
        The model identifier is split into namespace and name:
        `ai/smollm2` → namespace `ai`, name `smollm2`;
        `hf.co/org/repo` → namespace `hf.co`, name `org/repo`;
        bare names like `smollm2` default to namespace `ai`.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a model from Docker Model Runner",
            full = true,
            code = """
                id: docker_model_delete
                namespace: company.team

                tasks:
                  - id: delete_model
                    type: io.kestra.plugin.docker.model.Delete
                    model: ai/smollm2
                """
        )
    }
)
public class Delete extends AbstractModel implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Model identifier",
        description = "The model to delete, e.g. `ai/smollm2` or `hf.co/org/repo`."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> model;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rModel = runContext.render(this.model).as(String.class).orElseThrow();
        var rHost = resolvedHost(runContext);
        var id = ModelIdentifier.parse(rModel);

        var url = rHost + "/models/" + id.namespace() + "/" + id.name();
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .DELETE()
            .build();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("DMR DELETE " + url + " returned HTTP " + response.statusCode());
            }
        }
        runContext.logger().info("Deleted model {}", rModel);
        return null;
    }
}
