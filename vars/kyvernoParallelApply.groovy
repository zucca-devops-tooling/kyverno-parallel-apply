import dev.zucca_ops.Configuration
import dev.zucca_ops.FileDistributor
import dev.zucca_ops.ReportMerger
import dev.zucca_ops.WorkspaceManager

/**
 * Main entry point for the shared library call.
 * Example usage in Jenkinsfile:
 *
 * kyvernoParallelApply(
 * manifestSourceDirectory: 'path/to/kustomize-output',
 * policyPath: 'path/to/policies',
 * parallelStageCount: 8,
 * extraKyvernoArgs: '--cluster --audit-warn'
 * )
 */
def call(Map params = [:]) {
    // 1. INITIALIZE BUSINESS OBJECTS
    // The 'this' object in a 'vars' script is a reference to the pipeline steps provider.
    // It's passed to our helper classes so they can call steps like 'sh', 'readFile', etc.
    def config = new Configuration(params, this)
    config.loadConfig()

    // Use the Jenkins BUILD_NUMBER to create a unique workspace for each run, preventing conflicts.
    def workspace = new WorkspaceManager(pwd(), env.BUILD_NUMBER)

    // The distributor needs to know about the workspace layout and configuration.
    def distributor = new FileDistributor(workspace, config, this)

    // The merger needs to know where to find the partial reports.
    def merger = new ReportMerger(workspace, this)

    // 2. EXECUTE THE PIPELINE LOGIC
    // A try/finally block is crucial to ensure the workspace is always cleaned up,
    // even if one of the stages fails.
    try {
        // --- SETUP STAGE ---
        stage('Setup Parallel Workspace') {
            workspace.createDirectories(this, config.parallelStageCount)
            distributor.distribute()
        }

        // A map to store the results from each parallel stage
        def stageResults = [:]

        // --- PARALLEL EXECUTION STAGE ---
        stage('Parallel Kyverno Apply') {
            def parallelStages = [:]

            def policyPath = workspace.getFolder(config.policyPath)
            def generatedResourcesDir = workspace.getFolder(config.generatedResourcesDir)
            def valuesFileCommand = config.valuesFilePath != null
                    ? " --values-file '${workspace.getFolder(config.valuesFilePath)}'"
                    : ""

            if (config.debugLogDir) {
                sh "mkdir -p ${workspace.getFolder(config.debugLogDir)}"
            }

            // Ensure the final directory for generated resources exists before we start
            if (generatedResourcesDir) {
                sh "mkdir -p ${generatedResourcesDir}"
            }

            for (int i = 0; i < config.parallelStageCount; i++) {
                final int shardIndex = i

                parallelStages["Shard ${shardIndex}"] = {
                    node {
                        stage("Apply on Shard ${shardIndex}") {
                            try {
                                def shardDir = workspace.getShardDirectory(shardIndex)

                                def stdErrRedirect = config.debugLogDir != null
                                        ? " 2> '${workspace.getShardLogFile(config.debugLogDir, shardIndex)}'"
                                        : ""

                                def commandParts = [
                                        "kyverno", "apply",
                                        "'${policyPath}'",
                                        "--resource='${shardDir}'",
                                        "--audit-warn",
                                        "--cluster",
                                        "-v ${config.kyvernoVerbosity}",
                                        "-o ${generatedResourcesDir}",
                                        "--policy-report"
                                ]

                                def command = commandParts.join(' ')
                                def reportOutput = " > '${shardDir}/report.yaml'"

                                sh """
                                    # Temporarily disable 'exit on error' to allow us to capture the exit code
                                    set +e
                                    # Execute the command and redirect the output. The redirection will now complete.
                                    ${command} ${reportOutput} ${stdErrRedirect}
                                    # Capture the real exit code of the kyverno command
                                    EXIT_CODE=\$?
                                    # Re-enable 'exit on error' for the rest of the script
                                    set -e

                                    # Now, we intelligently check the exit code.
                                    # If it's greater than 1, it was a real crash, not a policy violation.
                                    if [ \$EXIT_CODE -gt 1 ]; then
                                        echo "Kyverno command failed with a critical error (exit code: \$EXIT_CODE)."
                                        exit \$EXIT_CODE
                                    fi
                                """

                                stageResults[shardIndex] = [status: 'SUCCESS']
                            } catch (Exception e) {
                                // If sh() fails, the exception is caught here.
                                echo "ERROR: Shard ${shardIndex} failed!"
                                stageResults[shardIndex] = [status: 'FAILURE', error: e.message]
                                // We do NOT re-throw the error, allowing other stages to continue.
                            }
                        }
                    }
                }
            }

            parallel(parallelStages)

            echo "All parallel stages complete. Analyzing results..."
            stageResults.each { index, result ->
                if (result.status == 'FAILURE') {
                    echo "Shard ${index} had a failure: ${result.error}"
                }
            }
        }

        // --- MERGE RESULTS STAGE ---
        stage('Merge Policy Reports') {
            def finalReport = merger.merge(config.parallelStageCount)
            def resultsDirectory = workspace.getResultDirectory()

            sh "mkdir -p ${resultsDirectory}"
            def finalReportPath = "${resultsDirectory}/final-report.yaml"

            // Use the Jenkins-native writeYaml step to serialize the final report.
            writeYaml(
                    file: finalReportPath,
                    data: finalReport.toMap(),
                    overwrite: true
            )

            echo "Final merged report created successfully at ${finalReportPath}"

            // Archive the final report so it's easily accessible from the Jenkins build page.
            archiveArtifacts artifacts: finalReportPath, followSymlinks: false
        }

        echo "Finalizing build status..."
        boolean hasFailures = stageResults.any { it.value.status == 'FAILURE' }
        if (hasFailures) {
            error "One or more Kyverno parallel stages failed. Check logs for details."
        }
    } finally {
        // --- CLEANUP STAGE ---
        stage('Cleanup Workspace') {
            workspace.cleanup(this)
        }
    }
}