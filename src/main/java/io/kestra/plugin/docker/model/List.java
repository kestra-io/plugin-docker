package io.kestra.plugin.docker.model;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List models available in Docker Model Runner",
    description = "Fetches the list of locally available models from the Docker Model Runner (DMR) REST API."
)
@Plugin(
    examples = {
        @Example(
            title = "List available Docker Model Runner models",
            full = true,
            code = """
                id: docker_model_list
                namespace: company.team

                tasks:
                  - id: list_models
                    type: io.kestra.plugin.docker.model.List
                """
        )
    }
)
public class List extends AbstractModel implements RunnableTask<List.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rHost = resolvedHost(runContext);

        var request = HttpRequest.builder()
            .uri(URI.create(rHost + "/models"))
            .method("GET")
            .addHeader("Accept", "application/json")
            .build();

        try (var client = httpClient(runContext)) {
            ModelsResponse body = client.request(request, ModelsResponse.class).getBody();
            java.util.List<ModelInfo> models = body != null && body.models() != null ? body.models() : java.util.List.of();
            runContext.logger().info("Found {} model(s)", models.size());
            return Output.builder().models(models).build();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelsResponse(java.util.List<ModelInfo> models) {}

    @Schema(title = "Information about a single model")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelInfo(
        @Schema(title = "Model identifier") String id,
        @Schema(title = "Creation timestamp (Unix seconds)") Long created,
        @Schema(title = "Model owner") @JsonProperty("owned_by") String ownedBy
    ) {}

    @Builder
    @Getter
    @Schema(title = "Output of the List task")
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Models available in Docker Model Runner")
        private final java.util.List<ModelInfo> models;
    }
}
