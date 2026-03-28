# Kyverno Parallel Apply Jenkins Shared Library

`kyverno-parallel-apply` is a Jenkins Shared Library that speeds up large `kyverno apply` runs by splitting a manifest directory into shards, running Kyverno in parallel, and merging the shard reports into a single final policy report.

It is aimed at CI pipelines that already produce a large folder of rendered Kubernetes manifests and want faster policy evaluation without having to build the orchestration logic in every `Jenkinsfile`.

## What The Library Does

For each invocation, the library:

1. Validates the input configuration.
2. Creates a temporary workspace under `.workspace/run-<BUILD_NUMBER>`.
3. Distributes manifest files across shard directories using a content hash.
4. Runs `kyverno apply` once per shard in parallel.
5. Merges the partial policy reports into one final report.
6. Archives the merged report as a Jenkins build artifact.

## Requirements

Before using the library, make sure the Jenkins environment provides:

- A Jenkins Shared Library entry pointing to this repository.
- A Unix-like agent environment. The library uses `sh`, `find`, `cp`, `mkdir`, `rm`, `sha256sum`, `awk`, and `cut`.
- The `kyverno` CLI available on `PATH` for the executors that run the parallel stages.
- The Pipeline Utility Steps plugin, because the library uses `readYaml` and `writeYaml`.
- Parallel executors that can access the same absolute workspace path created during the setup stage. The library does not use `stash` or `unstash`.

## Installation In Jenkins

Configure the library in `Manage Jenkins` -> `Configure System` -> `Global Pipeline Libraries`.

- Name: `kyverno-parallel-apply`
- Default version: pin a tag for production use, for example `v1.0.0`
- Retrieval method: Git
- Project repository: this repository URL

If you need background on library setup, see the [Jenkins Shared Library documentation](https://www.jenkins.io/doc/book/pipeline/shared-libraries/).

## Quick Start

With the default configuration, the library expects:

- manifests in `./kustomize-output`
- policies in `./policies`

Example:

```groovy
@Library('kyverno-parallel-apply@v1.0.0') _

pipeline {
    agent any

    stages {
        stage('Kyverno Scan') {
            steps {
                script {
                    kyvernoParallelApply()
                }
            }
        }
    }
}
```

## Typical Usage

```groovy
@Library('kyverno-parallel-apply@v1.0.0') _

pipeline {
    agent any

    stages {
        stage('Render Manifests') {
            steps {
                sh '''
                    mkdir -p kustomize-output
                    kustomize build overlays/dev > kustomize-output/all.yaml
                '''
            }
        }

        stage('Parallel Kyverno Apply') {
            steps {
                script {
                    kyvernoParallelApply(
                        manifestSourceDirectory: 'path/to/kustomize-output',
                        policyPath: 'path/to/policies',
                        parallelStageCount: 8,
                        extraKyvernoArgs: '--cluster --audit-warn',
                        valuesFilePath: 'path/to/values.yaml',
                        debugLogDir: 'logs/kyverno'
                    )
                }
            }
        }
    }
}
```

## Configuration

You can pass configuration directly from the `Jenkinsfile`, or load it from a YAML file with `configFile`.

Precedence is:

1. Built-in defaults
2. Values loaded from `configFile`
3. Values passed directly to `kyvernoParallelApply(...)`

### Configuration File Example

```yaml
# ci/kyverno-parallel.yaml
manifestSourceDirectory: "kustomize-output"
policyPath: "policies"
parallelStageCount: 8
extraKyvernoArgs: "--cluster --audit-warn"
valuesFilePath: "kyverno/values.yaml"
debugLogDir: "logs/kyverno"
generatedResourcesDir: "generated-resources"
kyvernoVerbosity: 2
```

```groovy
kyvernoParallelApply(configFile: 'ci/kyverno-parallel.yaml')
```

Direct parameters still win over values from the file:

```groovy
kyvernoParallelApply(
    configFile: 'ci/kyverno-parallel.yaml',
    parallelStageCount: 12
)
```

### Supported Parameters

All parameters below are optional.

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `manifestSourceDirectory` | `String` | `./kustomize-output` | Directory containing the manifest files that Kyverno should scan. |
| `policyPath` | `String` | `./policies` | File or directory containing the Kyverno policies to apply. |
| `parallelStageCount` | `Integer` | `4` | Number of parallel Kyverno shards to run. Keep it at or below the number of executors that can access the same workspace path. |
| `generatedResourcesDir` | `String` | `generated-resources` | Directory where Kyverno writes generated or mutated resources. |
| `debugLogDir` | `String` | `null` | If set, `stderr` from each shard is written to `shard-<n>-debug.log` files in this directory. If not set, `stderr` stays in the build log. |
| `kyvernoVerbosity` | `Integer` | `2` | Value passed to `kyverno apply -v`. |
| `extraKyvernoArgs` | `String` | `""` | Extra flags appended to `kyverno apply`. Do not include `-o` or `--output`. |
| `valuesFilePath` | `String` | `null` | Optional path passed to Kyverno with `--values-file`. |
| `configFile` | `String` | `null` | YAML file loaded before direct parameters are applied. |

## Outputs

The library produces these artifacts during a run:

- Merged policy report: `.workspace/run-<BUILD_NUMBER>/results/final-report.yaml`
- Generated resources: written under `generatedResourcesDir`
- Optional debug logs: `<debugLogDir>/shard-<n>-debug.log`

The merged report is archived with `archiveArtifacts`, so it is available from the Jenkins build page even after the temporary workspace is cleaned up.

## Current Behavior And Limits

These points are worth knowing before you depend on the library in production:

- The build is marked failed after the parallel stage completes if any shard failed.
- `failFast` is not currently wired into the `parallel(...)` call, so there is no supported fail-fast mode yet.
- The report filename is currently fixed to `final-report.yaml`.
- Sharding copies files into flat shard directories. If your source tree contains duplicate filenames in different subdirectories, later copies can overwrite earlier ones inside a shard.
- Absolute-path handling is Unix-style. Windows-style paths are not supported by the current implementation.
- `parallelStageCount: 1` is supported. In that case the library skips sharding and runs Kyverno directly against `manifestSourceDirectory`.

## Demo Repository

A full example that uses this library with `kustom-trace` lives here:

- [kustomize-at-scale-demo](https://github.com/zucca-devops-tooling/kustomize-at-scale-demo)

## Development

This repository includes unit tests for the library classes. Typical local commands are:

```bash
./gradlew test
./gradlew clean assemble
```
