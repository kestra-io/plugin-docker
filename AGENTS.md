# Kestra Docker Plugin

## What

- Provides plugin components under `io.kestra.plugin.docker`.
- Includes classes such as `PushResponseItemCallback`, `Build`, `Compose`, `Run`.

## Why

- What user problem does this solve? Teams need to docker tasks for building images, running containers, and managing artifacts from Kestra workflows from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Docker steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Docker.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `docker`

### Key Plugin Classes

- `io.kestra.plugin.docker.Build`
- `io.kestra.plugin.docker.Compose`
- `io.kestra.plugin.docker.Prune`
- `io.kestra.plugin.docker.Pull`
- `io.kestra.plugin.docker.Push`
- `io.kestra.plugin.docker.Rm`
- `io.kestra.plugin.docker.Run`
- `io.kestra.plugin.docker.Stop`
- `io.kestra.plugin.docker.Tag`

### Project Structure

```
plugin-docker/
├── src/main/java/io/kestra/plugin/docker/
├── src/test/java/io/kestra/plugin/docker/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
