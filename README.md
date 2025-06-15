# Kyverno Parallel Apply Jenkins Shared Library

This repository contains `kyverno-parallel-apply`, a Jenkins Shared Library designed to solve a critical performance bottleneck in CI/CD pipelines by parallelizing the execution of `kyverno apply`.

The library automates the sharding of Kubernetes manifests, runs `kyverno apply` concurrently across multiple Jenkins agent executors, and aggregates the results into a single, clean policy report.

## Live Demonstration

A full, working implementation of this library, alongside its companion tool **[kustom-trace](https://github.com/zucca-devops-tooling/kustom-trace)**, can be found in the demo repository here:

* **[https://github.com/zucca-devops-tooling/kustomize-at-scale-demo](https://github.com/zucca-devops-tooling/kustomize-at-scale-demo)**

This demo showcases how to use the library to test a large and complex set of real-world Kubernetes resources and highlights the significant performance improvements achieved through parallelism.

---

## Getting Started

### 🔧 Quick Start (Default Config)

For a simple setup, you can call the library with no parameters. It will use the default values for all options (e.g., look for policies in `./policies` and resources in `./kustomize-output`).

```groovy
@Library('kyverno-parallel-apply@v1.0.0') _

// ... inside a pipeline stage ...
script {
    kyvernoParallelApply()
}
```

### Jenkins Installation

To use this library, a Jenkins administrator must configure it under **Manage Jenkins** -> **Configure System** -> **Global Pipeline Libraries**. For more information on how shared libraries work, please see the official [Jenkins Shared Library Documentation](https://www.jenkins.io/doc/book/pipeline/shared-libraries/).

* **Name:** `kyverno-parallel-apply` (or a name of your choice).
* **Default version:** A specific Git tag is recommended for production use (e.g., `v1.0.0`).
* **Source Code Management:** Git.
* **Project Repository:** The URL of this Git repository.

### Usage in `Jenkinsfile`

Once installed, import the library and call it from a `script` block within one of your pipeline stages.

```groovy
// At the top of your Jenkinsfile
@Library('kyverno-parallel-apply@v1.0.0') _

pipeline {
    agent any
    
    stages {
        // ... previous stages for checking out code, building manifests, etc. ...

        stage('Parallel Kyverno Scan') {
            steps {
                script {
                    // Call the library with your desired configuration
                    kyvernoParallelApply(
                        manifestSourceDirectory: 'path/to/kustomize-output',
                        policyPath:              'path/to/policies',
                        parallelStageCount:      8
                    )
                }
            }
        }
    }
}
```

---

## Configuration Reference

The library can be configured by passing parameters directly in the `Jenkinsfile` or by providing a path to a YAML configuration file.

### Configuration via File

You can manage all configuration in a central `configFile`. This is useful for keeping your `Jenkinsfile` clean.

**Example `config.yaml`:**
```yaml
# ci/config.yaml
manifestSourceDirectory: "kustomize-output/"
policyPath: "policies/"
parallelStageCount: 8
extraKyvernoArgs: "--cluster"
debugLogDir: "logs/debug"
```

**`Jenkinsfile` Usage:**
```groovy
// The library will load all values from the specified file.
kyvernoParallelApply(configFile: 'ci/config.yaml')

// You can still override specific values from the file.
// In this case, failFast will be true, but all other values come from the file.
kyvernoParallelApply(
    configFile: 'ci/config.yaml',
    failFast: true
)
```
> **Note:** Any parameters passed directly in the `kyvernoParallelApply` map will always take precedence over values defined in the `configFile`.

### All Parameters

All parameters are optional.

| Parameter | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `manifestSourceDirectory`| `String` | `./kustomize-output` | The path to the directory containing the Kubernetes manifest files to be scanned. |
| `policyPath` | `String` | `./policies` | The path to the file or directory containing the Kyverno policies to apply. |
| `generatedResourcesDir` | `String` | `null` | **If provided**, all mutated/generated resources from Kyverno will be saved to this directory. If omitted, they are not saved. |
| `debugLogDir` | `String` | `null` | **If provided**, all `stderr` logs from each parallel Kyverno process will be saved to unique files in this directory. If omitted, logs print to the console. |
| `parallelStageCount` | `Integer`| `4` | The number of parallel stages to execute. Should not exceed the number of available executors on your Jenkins agent. |
| `kyvernoVerbosity` | `Integer`| `2` | The verbosity level (`-v`) to pass to the `kyverno` CLI. Ranges from 0 (quiet) to 9 (debug). |
| `extraKyvernoArgs` | `String` | `""` | A string of any additional flags to pass directly to the `kyverno` CLI (e.g., `'--cluster --audit-warn'`). |
| `valuesFilePath` | `String` | `null` | The path to a Kyverno values file to be passed via the `--values-file` flag. |
| `failFast` | `Boolean`| `false` | If `true`, the entire parallel block will stop as soon as the first shard fails. If `false`, all shards will run to completion. |
| `configFile` | `String` | `null` | The path to a YAML file containing configuration parameters. |

