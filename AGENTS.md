# Kestra Docker Plugin

## What

- Provides plugin components under `io.kestra.plugin.docker`.
- Includes classes such as `PushResponseItemCallback`, `Build`, `Compose`, `Run`, `Agent`.

## Why

- What user problem does this solve? Teams need to docker tasks for building images, running containers, and managing artifacts from Kestra workflows from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Docker steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Docker.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `docker`
- `docker.cli`
- `docker.model`

### Key Plugin Classes

- `io.kestra.plugin.docker.cli.Agent` — run a Docker Agent team through `AbstractExecScript`; alias: `io.kestra.plugin.docker.Agent`
- `io.kestra.plugin.docker.Build`
- `io.kestra.plugin.docker.Compose`
- `io.kestra.plugin.docker.ImageLs`
- `io.kestra.plugin.docker.Prune`
- `io.kestra.plugin.docker.Pull`
- `io.kestra.plugin.docker.Push`
- `io.kestra.plugin.docker.Rm`
- `io.kestra.plugin.docker.Run`
- `io.kestra.plugin.docker.Stop`
- `io.kestra.plugin.docker.Tag`
- `io.kestra.plugin.docker.model.Pull` — pull a model via DMR REST API
- `io.kestra.plugin.docker.model.List` — list locally available models via DMR REST API
- `io.kestra.plugin.docker.model.Delete` — delete a model via DMR REST API

### Project Structure

```
plugin-docker/
├── src/main/java/io/kestra/plugin/docker/
├── src/main/java/io/kestra/plugin/docker/model/
├── src/test/java/io/kestra/plugin/docker/
├── src/test/java/io/kestra/plugin/docker/model/
├── build.gradle
└── README.md
```

### Testing

`io.kestra.plugin.docker.cli.AgentTest` covers configuration sources, rendering, command arguments, and image defaults. Its execution tests require Docker and run the pinned Docker Agent image against a local WireMock provider, without live model-provider calls. Run it with `./gradlew test --tests io.kestra.plugin.docker.cli.AgentTest`. Keep execution tests independent of live AI calls.

`io.kestra.plugin.docker.model.DeleteIT`, `PullIT`, and `ListIT` are integration tests that run against a **live** Docker Model Runner and are gated behind `@DockerModelRunnerTest`, which requires both `DMR_IT_TESTS=true` and a reachable DMR at `localhost:12434`. They are skipped by default (including in CI) and are **destructive**: `PullIT` and `DeleteIT` pull and delete the real `ai/smollm2` tag on whatever DMR instance is reachable. DMR tags are not test-namespaced, so a developer running with the opt-in set and an existing `ai/smollm2` model of their own will lose it. Only set `DMR_IT_TESTS=true` against a DMR instance you don't mind losing that model on.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
