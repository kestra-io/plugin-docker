# Kestra Docker Plugin

## What

- Provides plugin components under `io.kestra.plugin.docker`.
- Includes classes such as `PushResponseItemCallback`, `Build`, `Compose`, `Run`.

## Why

- This plugin integrates Kestra with Docker.
- It provides docker tasks for building images, running containers, and managing artifacts from Kestra workflows.

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
