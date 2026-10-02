<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<div align="center">
 <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

<p align="center">
  <a href="https://twitter.com/kestra_io" style="margin: 0 10px;">
        <img src="https://kestra.io/twitter.svg" alt="twitter" width="35" height="25" /></a>
  <a href="https://www.linkedin.com/company/kestra/" style="margin: 0 10px;">
        <img src="https://kestra.io/linkedin.svg" alt="linkedin" width="35" height="25" /></a>
  <a href="https://www.youtube.com/@kestra-io" style="margin: 0 10px;">
        <img src="https://kestra.io/youtube.svg" alt="youtube" width="35" height="25" /></a>
</p>

<br />
<p align="center">
    <a href="https://go.kestra.io/video/product-overview" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 4 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>Get started with Kestra in 4 minutes.</i></p>

# Kestra Docker Plugin

## Why

- What user problem does this solve? Teams need to docker tasks for building images, running containers, and managing artifacts from Kestra workflows from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Docker steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Docker.

## What

- Provides plugin components under `io.kestra.plugin.docker`.
- Includes classes such as `PushResponseItemCallback`, `Build`, `Compose`, `Run`, `Agent`.

## Run a Docker Agent team

Use `io.kestra.plugin.docker.cli.Agent` (also available as `io.kestra.plugin.docker.Agent`) to run a [Docker Agent](https://docs.docker.com/ai/docker-agent/) team from a flow. The task accepts inline YAML, a relative configuration path in the task working directory, or a Kestra internal-storage URI. Pass the assignment through `prompt` and model provider API keys through `env` using secrets.

```yaml
id: docker_agent_review
namespace: company.team

tasks:
  - id: review
    type: io.kestra.plugin.docker.cli.Agent
    taskRunner:
      type: io.kestra.plugin.scripts.runner.docker.Docker
      user: root
    env:
      OPENAI_API_KEY: "{{ secret('OPENAI_API_KEY') }}"
    prompt: "Review these release notes for breaking changes: v2 removes the legacy /v1 API and adds CSV export."
    agentConfig: |
      agents:
        root:
          model: openai/gpt-5
          instruction: You review release notes for breaking changes.
```

The Docker task runner requires a reachable Docker daemon. The default image is `docker/docker-agent:1.146.0`; override `containerImage` to use a custom image that provides `/docker-agent`. Execution uses headless mode, streams the agent's output to Kestra logs, and returns `ScriptOutput`, including `exitCode`. Set the Docker runner `user: root` so the image can read Kestra temporary files. Provider credentials are required for the chosen model.

See the [Docker plugin how-to](src/main/resources/doc/io.kestra.plugin.docker.md#docker-agent) for uploaded configuration files, scheduling, and output behavior.

## Documentation
* Full documentation can be found under: [kestra.io/docs](https://kestra.io/docs)
* Documentation for developing a plugin is included in the [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/)


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)


## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)
