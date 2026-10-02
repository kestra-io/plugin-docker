package io.kestra.plugin.docker.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class AgentTest {
    private static final String CONFIG = """
        agents:
          root:
            model: openai/gpt-5
            instruction: Review release notes.
        """;

    @Inject
    RunContextFactory runContextFactory;

    private Agent task(String config) {
        Property<String> agentConfig = null;
        if (config != null) {
            agentConfig = config.contains("{{") ? Property.ofExpression(config) : Property.ofValue(config);
        }
        return Agent.builder()
            .id("agent-test")
            .type(Agent.class.getName())
            .agentConfig(agentConfig)
            .build();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            CONFIG,
            "agents: {root: {model: openai/gpt-5}}",
            "{agents: {root: {model: openai/gpt-5}}}"
        }
    )
    void inlineYamlIsWrittenToTemporaryFile(String config) throws Exception {
        var task = task(config);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        Path resolved = task.resolveAgentConfig(runContext);

        assertThat(Files.readString(resolved), is(config));
        assertThat(resolved.getFileName().toString().endsWith(".yaml"), is(true));
        assertThat(resolved.getParent(), is(runContext.workingDir().path()));
    }

    @Test
    void relativeFileIsCopiedWithoutChangingSource() throws Exception {
        var task = task("configs/agent.yaml");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Path source = runContext.workingDir().resolve(Path.of("configs/agent.yaml"));
        Files.createDirectories(source.getParent());
        Files.writeString(source, CONFIG);

        Path resolved = task.resolveAgentConfig(runContext);

        assertThat(resolved, not(is(source)));
        assertThat(Files.readString(resolved), is(CONFIG));
        Files.writeString(resolved, "changed");
        assertThat(Files.readString(source), is(CONFIG));
    }

    @Test
    void storageFileInputIsRenderedAndDownloadedWithoutChangingSource() throws Exception {
        var task = task("{{ inputs.config }}");
        var storageContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var uri = storageContext.storage().putFile(
            new ByteArrayInputStream(CONFIG.getBytes(StandardCharsets.UTF_8)), "agent.yaml"
        );
        try {
            var runContext = TestsUtils.mockRunContext(
                runContextFactory, task, Map.of("config", uri.toString())
            );

            Path resolved = task.resolveAgentConfig(runContext);

            assertThat(Files.readString(resolved), is(CONFIG));
            Files.writeString(resolved, "changed");
            try (var input = storageContext.storage().getFile(uri)) {
                assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8), is(CONFIG));
            }
        } finally {
            storageContext.storage().deleteFile(uri);
        }
    }

    @Test
    void missingConfigurationIsRejected() {
        var task = task(null);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(ConstraintViolationException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), containsString("agentConfig"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\n\t" })
    void blankConfigurationIsRejected(String config) {
        var task = task(config);
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), is("agentConfig must not be blank."));
    }

    @Test
    void missingRelativeFileIsRejected() {
        var task = task("missing.yaml");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), containsString("file does not exist or is not a regular file"));
    }

    @Test
    void directoryIsRejected() throws Exception {
        var task = task("configs");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Files.createDirectory(runContext.workingDir().resolve(Path.of("configs")));

        var exception = assertThrows(IllegalArgumentException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), containsString("file does not exist or is not a regular file"));
    }

    @Test
    void missingStorageFileFails() {
        var task = task("kestra:///missing-agent-config.yaml");
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        assertThrows(IOException.class, () -> task.resolveAgentConfig(runContext));
    }

    @Test
    void absoluteFilePathIsRejected() {
        var task = task(Path.of("agent.yaml").toAbsolutePath().toString());
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> task.resolveAgentConfig(runContext));

        assertThat(exception.getMessage(), containsString("must be relative to the working directory"));
    }
}
