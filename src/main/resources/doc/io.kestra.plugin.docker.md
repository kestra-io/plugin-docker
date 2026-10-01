# How to use the Docker plugin

Manage Docker images and containers from Kestra flows — building, pushing, running, and composing — against a local or remote Docker daemon.

## Authentication

The Kestra worker must have access to a Docker daemon. By default the plugin connects via the local Unix socket (`/var/run/docker.sock`); set `host` to a TCP endpoint (e.g., `tcp://remote-host:2376`) to use a remote daemon.

For private registries, set `credentials.registry`, `credentials.username`, and `credentials.password` on each task that pulls or pushes images. Store credentials in [secrets](https://kestra.io/docs/concepts/secret). When no credentials are set, Docker Hub public images are used without authentication.

## Tasks

`Run` is the primary task — it starts a container from an image, streams stdout as task output, and waits for exit. Use it when you need to execute a containerized tool or process as a step in a flow.

For CI/CD automation, `Build` builds an image from a Dockerfile, `Tag` applies additional tags, and `Push` uploads an image to a registry. `Pull` pre-fetches an image explicitly. `Compose` runs a multi-container stack from a `docker-compose.yml` file and is useful for integration testing or spinning up dependent services. `ImageLs` lists the images available on the host. `Stop` and `Rm` manage container lifecycle; `Prune` cleans up unused resources.

If your goal is running a script inside a container as part of a flow, use a [Docker task runner](https://kestra.io/docs/task-runners) on a script task rather than the Docker plugin — the plugin is intended for managing Docker artifacts and infrastructure, not for script execution isolation.

## Docker Agent

The `io.kestra.plugin.docker.cli.Agent` task runs a Docker Agent team in headless mode from a Kestra flow.

### Configuration

- `agentConfig` is required and accepts inline YAML, a relative path in the task working directory, or a `kestra://` URI.
- `prompt` is optional. When provided, the task sends it to Docker Agent through standard input, which also supports multiline prompts without putting the prompt text into the shell command.
- `containerImage` defaults to Docker's `docker/docker-agent:1.145.0` image.
- Provider API keys belong in the inherited `env` property, for example `OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"`.

Docker Agent's headless `--exec` mode is intended for scripts and CI and exits when the run completes. Its `--json` option produces an NDJSON event stream, but the task does not parse that stream into a custom answer output; `ScriptOutput` remains the task output contract.

The default image contains the standalone `/docker-agent` binary. The task resolves `docker-agent` through a controlled `PATH` that includes `/`, so custom `containerImage` values can instead expose `docker-agent` through their normal `PATH`.

### Example

```yaml
id: docker_agent_review
namespace: company.team

tasks:
  - id: review
    type: io.kestra.plugin.docker.cli.Agent
    taskRunner:
      type: io.kestra.plugin.scripts.runner.docker.Docker
    env:
      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
    prompt: "Review the release notes and list breaking changes."
    agentConfig: |
      agents:
        root:
          model: openai/gpt-5
          description: Release notes reviewer
          instruction: You review release notes for breaking changes.
```

Docker Agent provider credentials should be supplied through `env` and secrets rather than embedded in the agent configuration.

## Docker Model Runner

The `io.kestra.plugin.docker.model` subpackage manages AI models through the Docker Model Runner (DMR) REST API, rather than through the Docker daemon. `host` on these tasks is a completely different setting from `AbstractDocker.host` above: it is DMR's own REST endpoint (defaults to `http://localhost:12434`), not a Docker daemon socket or TCP address, and it has no equivalent authentication mechanism, and DMR does not require credentials.

`List` fetches the models locally available on the DMR instance, including their content digest, tags, creation time, and configuration (format, quantization, parameter count, architecture, size). `Pull` downloads a model from a registry, e.g. `ai/smollm2`, streaming progress as it goes. `Delete` removes a locally available model; the model identifier is split into a namespace and a name (`ai/smollm2` → namespace `ai`, name `smollm2`; a bare name like `smollm2` defaults to namespace `ai`).
