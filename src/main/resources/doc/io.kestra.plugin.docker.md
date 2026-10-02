# How to use the Docker plugin

Manage Docker images and containers from Kestra flows — building, pushing, running, and composing — against a local or remote Docker daemon. Run Docker Agent teams through a task runner using the `Agent` task.

## Authentication

The Kestra worker must have access to a Docker daemon. By default the plugin connects via the local Unix socket (`/var/run/docker.sock`); set `host` to a TCP endpoint (e.g., `tcp://remote-host:2376`) to use a remote daemon.

For private registries, set `credentials.registry`, `credentials.username`, and `credentials.password` on each task that pulls or pushes images. Store credentials in [secrets](https://kestra.io/docs/concepts/secret). When no credentials are set, Docker Hub public images are used without authentication.

## Tasks

`Run` is the primary task — it starts a container from an image, streams stdout as task output, and waits for exit. Use it when you need to execute a containerized tool or process as a step in a flow.

For CI/CD automation, `Build` builds an image from a Dockerfile, `Tag` applies additional tags, and `Push` uploads an image to a registry. `Pull` pre-fetches an image explicitly. `Compose` runs a multi-container stack from a `docker-compose.yml` file and is useful for integration testing or spinning up dependent services. `ImageLs` lists the images available on the host. `Stop` and `Rm` manage container lifecycle; `Prune` cleans up unused resources.

If your goal is running a script inside a container as part of a flow, use a [Docker task runner](https://kestra.io/docs/task-runners) on a script task rather than the Docker plugin — the plugin is intended for managing Docker artifacts and infrastructure, not for script execution isolation.

## Docker Agent

`io.kestra.plugin.docker.cli.Agent`, also available through the alias `io.kestra.plugin.docker.Agent`, runs a [Docker Agent](https://docs.docker.com/ai/docker-agent/) team using the existing script execution framework. Docker Agent supplies the agent runtime and tools; the Kestra task prepares the configuration and invokes the CLI.

### Configuration and execution

| Property | Purpose |
| --- | --- |
| `agentConfig` | Required Docker Agent configuration: inline YAML, a relative path in the task working directory, or a `kestra://` internal-storage URI. Each source is staged into a temporary YAML file. |
| `prompt` | Optional initial assignment. Kestra expressions are rendered and the result is passed as one argument. |
| `containerImage` | Execution image, defaulting to `docker/docker-agent:1.146.0`. Custom images must provide `/docker-agent`. |
| `env` | Inherited environment variables, including the credentials required by the configured model provider. Use Kestra secrets. |
| `taskRunner` | Inherited execution runner. The examples use the Docker task runner. |

The task invokes `/docker-agent run --exec <temporary-config.yaml> [prompt]`. `--exec` selects headless execution; the task does not enable JSON output or extract a separate final-answer field. The pinned [Docker image](https://hub.docker.com/r/docker/docker-agent/tags) contains the standalone binary at `/docker-agent`, rather than registering `docker agent` as a CLI plugin. See the [versioned Dockerfile](https://github.com/docker/docker-agent/blob/v1.146.0/Dockerfile) and [CLI reference](https://docker.github.io/docker-agent/features/cli/).

The Docker task runner requires a reachable Docker daemon and clears the image entrypoint by default so the command can run directly. Installing Docker Agent on a developer's computer does not install it inside the execution image. Keep the runner's default entrypoint when using the examples below.

A relative configuration path must refer to a regular file already present in the task working directory. Missing files fail the task; the original file is preserved. For uploaded configurations, use a `FILE` input as shown below. The configuration must declare the tools and data access needed for the assignment; the task does not grant access to incidents, repositories, or deployment records automatically.

### Inline configuration

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
    prompt: "Review these release notes for breaking changes: v2 removes the legacy /v1 API and adds CSV export."
    agentConfig: |
      agents:
        root:
          model: openai/gpt-5
          instruction: You review release notes for breaking changes.
```

### Uploaded configuration

Upload an agent YAML file using an OpenAI model, such as the configuration above. The `FILE` input resolves to a `kestra://` URI; the task downloads the file into the working directory. For other model providers, supply the corresponding API key through `env` instead.

```yaml
id: docker_agent_from_file
namespace: company.team

inputs:
  - id: config
    type: FILE

tasks:
  - id: run_team
    type: io.kestra.plugin.docker.cli.Agent
    taskRunner:
      type: io.kestra.plugin.scripts.runner.docker.Docker
    env:
      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
    agentConfig: "{{ inputs.config }}"
    prompt: "Create a checklist for reviewing a production deployment."

  - id: log_result
    type: io.kestra.plugin.core.log.Log
    message: "Agent exit code: {{ outputs.run_team.exitCode }}"
```

### Scheduled execution

```yaml
id: docker_agent_daily
namespace: company.team

triggers:
  - id: every_morning
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "0 7 * * *"

tasks:
  - id: daily_checklist
    type: io.kestra.plugin.docker.cli.Agent
    taskRunner:
      type: io.kestra.plugin.scripts.runner.docker.Docker
    env:
      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
    prompt: "Create a short checklist for today's deployment review."
    agentConfig: |
      agents:
        root:
          model: openai/gpt-5
          instruction: You write concise operations checklists.
```

### Outputs and failures

The task returns the existing `ScriptOutput`: `exitCode`, stdout/stderr line counts, and configured output files. Agent stdout and stderr are streamed to Kestra's execution logs; there is no separate `answer` output. API keys belong in `env`, not command arguments or log messages.

Nonzero CLI exits fail the task through the execution framework. Kestra's normal retry and error-handling mechanisms apply. The task inherits cancellation handling from `AbstractExecScript`, which delegates to the configured task runner.

## Docker Model Runner

The `io.kestra.plugin.docker.model` subpackage manages AI models through the Docker Model Runner (DMR) REST API, rather than through the Docker daemon. `host` on these tasks is a completely different setting from `AbstractDocker.host` above: it is DMR's own REST endpoint (defaults to `http://localhost:12434`), not a Docker daemon socket or TCP address, and it has no equivalent authentication mechanism, and DMR does not require credentials.

`List` fetches the models locally available on the DMR instance, including their content digest, tags, creation time, and configuration (format, quantization, parameter count, architecture, size). `Pull` downloads a model from a registry, e.g. `ai/smollm2`, streaming progress as it goes. `Delete` removes a locally available model; the model identifier is split into a namespace and a name (`ai/smollm2` → namespace `ai`, name `smollm2`; a bare name like `smollm2` defaults to namespace `ai`).
