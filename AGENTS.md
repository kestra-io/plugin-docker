# Kestra Docker Plugin

## What

Execute Docker commands as part of a Kestra workflow. Exposes 9 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with Docker, allowing orchestration of Docker-based operations as part of data pipelines and automation workflows.

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

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
