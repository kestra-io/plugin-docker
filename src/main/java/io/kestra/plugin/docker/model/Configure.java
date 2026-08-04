package io.kestra.plugin.docker.model;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    title = "Configure a model in Docker Model Runner",
    description = """
        Applies runtime configuration to a model via the Docker Model Runner (DMR) REST API.
        Only fields that are explicitly set are sent in the request body.
        If neither `contextSize` nor `runtimeFlags` is set, no HTTP call is made.
        Set `contextSize` to `-1` to reset the context window to the model default.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Configure a model's context size in Docker Model Runner",
            full = true,
            code = """
                id: docker_model_configure
                namespace: company.team

                tasks:
                  - id: configure_model
                    type: io.kestra.plugin.docker.model.Configure
                    model: ai/smollm2
                    contextSize: 4096
                    runtimeFlags:
                      - "--temp 0.7"
                """
        )
    }
)
public class Configure extends AbstractModel implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Model identifier",
        description = "The model to configure, e.g. `ai/smollm2` or `hf.co/org/repo`."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> model;

    @Schema(
        title = "Context window size",
        description = "Number of tokens in the model's context window. Pass `-1` to reset to the model default."
    )
    @PluginProperty(group = "advanced")
    private Property<Integer> contextSize;

    @Schema(
        title = "Runtime flags",
        description = "Raw llama.cpp inference flags, e.g. `--temp 0.7`. Applied verbatim to the model runtime."
    )
    @PluginProperty(group = "advanced")
    private Property<List<String>> runtimeFlags;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rContextSize = runContext.render(this.contextSize).as(Integer.class).orElse(null);
        var rRuntimeFlags = runContext.render(this.runtimeFlags).asList(String.class);

        if (rContextSize == null && rRuntimeFlags.isEmpty()) {
            runContext.logger().info("Neither contextSize nor runtimeFlags set; skipping configure call");
            return null;
        }

        var rModel = runContext.render(this.model).as(String.class).orElseThrow();
        var rHost = resolvedHost(runContext);
        var id = ModelIdentifier.parse(rModel);

        Map<String, Object> bodyMap = new LinkedHashMap<>();
        if (rContextSize != null) {
            bodyMap.put("contextSize", rContextSize);
        }
        if (!rRuntimeFlags.isEmpty()) {
            bodyMap.put("runtimeFlags", rRuntimeFlags);
        }

        var url = rHost + "/models/" + id.namespace() + "/" + id.name() + "/configure";
        var request = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.builder().content(bodyMap).build())
            .build();

        try (var client = httpClient(runContext)) {
            client.request(request, String.class);
        }
        runContext.logger().info("Configured model {}", rModel);
        return null;
    }
}
