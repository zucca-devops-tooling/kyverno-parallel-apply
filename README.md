# Kyverno Parallel Apply Jenkins Shared Library

This repository contains `kyverno-parallel-apply`, a Jenkins Shared Library designed to solve a critical performance bottleneck in CI/CD pipelines by parallelizing the execution of `kyverno apply`.

The library automates the sharding of Kubernetes manifests, runs `kyverno apply` concurrently across multiple Jenkins agent executors, and aggregates the results into a single, clean policy report.

## Live Demonstration

A full, working implementation of this library, alongside its companion tool **[kustom-trace](https://github.com/zucca-devops-tooling/kustom-trace)**, can be found in the demo repository here:

* **[https://github.com/zucca-devops-tooling/kustomize-at-scale-demo](https://github.com/zucca-devops-tooling/kustomize-at-scale-demo)**

This demo showcases how to use the library to test a large and complex set of real-world Kubernetes resources and highlights the significant performance improvements achieved through parallelism.

---

## Features

* **Parallel Execution:** Dramatically reduces `kyverno apply` execution time by running scans in parallel.
* **Configurable Parallelism:** The number of parallel stages is fully configurable.
* **Robust Output Handling:**
    * Intelligently parses the output from `kyverno`, separating the structured YAML report from informational log lines.
    * Saves generated/mutated resources to a user-defined directory.
    * Optionally captures all `stderr` debug logs from each parallel run into separate files.
* **Flexible Configuration:** Configure via `Jenkinsfile` parameters or a central YAML file.
* **Resilient Error Handling:** The library distinguishes between critical tool failures and simple policy violations, and can be configured to not fail fast, ensuring a complete report is always generated.

## Getting Started

### 1. Jenkins Installation

To use this library, a Jenkins administrator must configure it under **Manage Jenkins** -> **Configure System** -> **Global Pipeline Libraries**.

* **Name:** `kyverno-parallel-apply` (or a name of your choice).
* **Default version:** A specific Git tag is recommended for production use (e.g., `v1.0.0`). For development, you can use a branch name like `main`.
* **Source Code Management:** Git.
* **Project Repository:** The URL of this Git repository.

### 2. `Jenkinsfile` Usage

Once configured, import and call the library in your pipeline's script block.

```groovy
// At the top of your Jenkinsfile
@Library('kyverno-parallel-apply@v1.0.0') _

pipeline {
    agent any
    
    stages {
        // ... (previous stages for checking out code, building manifests, etc.) ...

        stage('Parallel Kyverno Scan') {
            steps {
                script {
                    // Call the library with your desired configuration
                    kyvernoParallelApply(
                        manifestSourceDirectory: 'path/to/kustomize-output',
                        policyPath:              'path/to/policies',
                        parallelStageCount:      8,
                        extraKyvernoArgs:        '--cluster --audit-warn',
                        generatedResourcesDir:   'generated-artifacts',
                        debugLogDir:             'debug-logs'
                    )
                }
            }
        }
    }
}
```

---

## Configuration Parameters

The library is configured by passing a map of parameters to the `kyvernoParallelApply()` call. All parameters are optional and have sensible defaults.

| Parameter | Type | Required | Default Value | Description |
| :--- | :--- | :--- | :--- | :--- |
| `manifestSourceDirectory`| `String` | No | `./kustomize-output` | The path to the directory containing the Kubernetes manifest files to be scanned. |
| `policyPath` | `String` | No | `./policies` | The path to the file or directory containing the Kyverno policies to apply. |
| `generatedResourcesDir` | `String` | No | `null` | **If provided**, all mutated/generated resources from Kyverno will be saved to this directory. If omitted, they are not saved. |
| `debugLogDir` | `String` | No | `null` | **If provided**, all `stderr` logs from each parallel Kyverno process will be saved to unique files in this directory. If omitted, logs print to the console. |
| `parallelStageCount` | `Integer`| No | `4` | The number of parallel stages to execute. Should not exceed the number of available executors on your Jenkins agent. |
| `kyvernoVerbosity` | `Integer`| No | `2` | The verbosity level (`-v`) to pass to the `kyverno` CLI. Ranges from 0 (quiet) to 9 (debug). |
| `extraKyvernoArgs` | `String` | No | `""` | A string of any additional flags to pass directly to the `kyverno` CLI (e.g., `'--cluster --audit-warn'`). |
| `valuesFilePath` | `String` | No | `null` | The path to a Kyverno values file to be passed via the `--values-file` flag. |
| `failFast` | `Boolean`| No | `false` | If `true`, the entire parallel block will stop as soon as the first shard fails. If `false`, all shards will run to completion. |
| `configFile` | `String` | No | `null` | The path to a YAML file containing configuration parameters. Any parameters passed directly in the map will override the values in this file. |
