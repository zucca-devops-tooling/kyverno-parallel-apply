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

        // --- PARALLEL EXECUTION STAGE ---
        stage('Parallel Kyverno Apply') {
            // Create a map to hold all the parallel stages.
            def parallelStages = [:]
            for (int i = 0; i < config.parallelStageCount; i++) {
                // Use a local variable in the loop to avoid closure scoping issues.
                final int shardIndex = i

                // Ask the workspace manager for the correct directory for this shard.
                def shardDir = workspace.getShardDirectory(shardIndex)

                // Define the closure for this parallel stage.
                parallelStages["Shard ${shardIndex}"] = {
                    node { // It's good practice to grab a node for each parallel stage
                        stage("Apply on Shard ${shardIndex}") {
                            echo "Running kyverno on manifests in ${shardDir}"

                            // Construct the kyverno command safely.
                            // The library controls the core command and output redirection.
                            def baseCommand = "kyverno apply \"${config.policyPath}\" --resource=\"${shardDir}\""
                            def reportOutput = "> \"${shardDir}/report.yaml\""

                            // Safely append any extra user-provided arguments.
                            sh "${baseCommand} ${config.extraKyvernoArgs} ${reportOutput}"
                        }
                    }
                }
            }
            // Execute all the defined stages in parallel.
            parallel parallelStages
        }

        // --- MERGE RESULTS STAGE ---
        stage('Merge Policy Reports') {
            def finalReport = merger.merge(config.parallelStageCount)
            def finalReportPath = "${workspace.getResultDirectory()}/final-report.yaml"

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

    } finally {
        // --- CLEANUP STAGE ---
        stage('Cleanup Workspace') {
            workspace.cleanup(this)
        }
    }
}