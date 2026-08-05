package io.kestra.plugin.docker.model;

import java.net.URI;

import io.kestra.core.http.HttpRequest;
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
        var rModel = runContext.render(this.model).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("The `model` property is required, e.g. `ai/smollm2`."));
        var rHost = resolvedHost(runContext);
        var id = ModelIdentifier.parse(rModel);

        var request = HttpRequest.builder()
            .uri(URI.create(rHost + "/models/" + id.namespace() + "/" + id.name()))
            .method("DELETE")
            .build();

        this.execute(runContext, request, String.class, "delete model '" + rModel + "'");
        runContext.logger().info("Deleted model {}", rModel);
        return null;
    }
}
