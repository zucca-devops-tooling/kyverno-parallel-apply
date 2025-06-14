package dev.zucca_ops

/**
 * This class is responsible for running the 'kyverno apply' command for a single shard.
 * It encapsulates the logic for command construction and execution, based on the user's
 * excellent design suggestion. It is designed to be called from a parallel stage.
 */
class KyvernoRunner {

    private final def config
    private final def workspace
    private final int shardIndex
    private final def steps

    KyvernoRunner(def config, def workspace, int shardIndex, def steps) {
        this.config = config
        this.workspace = workspace
        this.shardIndex = shardIndex
        this.steps = steps
    }

    /**
     * Executes the kyverno apply command for the configured shard.
     * This method contains the exact logic from the user's parallel stage.
     */
    Map run() {
        try {
            def shardDir = workspace.getShardDirectory(shardIndex)
            def policyPath = workspace.getFolder(config.policyPath)
            def generatedResourcesDir = workspace.getFolder(config.generatedResourcesDir)

            def valuesFileCommand = config.valuesFilePath != null
                    ? " --values-file '${workspace.getFolder(config.valuesFilePath)}'"
                    : ""

            def stdErrRedirect = config.debugLogDir != null
                    ? " 2> '${workspace.getShardLogFile(config.debugLogDir, shardIndex)}'"
                    : ""

            // Build the command parts exactly as specified.
            def commandParts = [
                    "kyverno", "apply",
                    "'${policyPath}'",
                    "--resource='${shardDir}'",
                    "-v ${config.kyvernoVerbosity}",
                    "-o ${generatedResourcesDir}",
                    "${valuesFileCommand}",
                    config.extraKyvernoArgs,
                    "--policy-report"
            ]

            def command = commandParts.join(' ')
            def reportOutput = " > '${shardDir}/report.yaml'"

            // Execute the final command. If this sh step fails, it will throw an
            // exception that can be caught by the calling parallel stage.
            steps.sh "${command} ${reportOutput} ${stdErrRedirect}"

            return [status: 'SUCCESS']
        } catch (Exception e) {
            // If sh() fails, the exception is caught here.
            steps.echo "ERROR: Shard ${shardIndex} failed!"
            return [status: 'FAILURE', error: e.message]
            // We do NOT re-throw the error, allowing other stages to continue.
        }
    }
}