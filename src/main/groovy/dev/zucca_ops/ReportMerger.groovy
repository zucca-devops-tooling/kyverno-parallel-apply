package dev.zucca_ops

class ReportMerger {

    private final WorkspaceManager workspace
    private final def steps

    ReportMerger(WorkspaceManager workspace, def steps) {
        this.workspace = workspace
        this.steps = steps
    }

    /**
     * Finds all partial reports from shard directories and merges them.
     * @param shardCount The number of shards to check for reports.
     * @return A single, merged PolicyReport object.
     */
    PolicyReport merge(int shardCount) {
        steps.echo "Starting merge process for ${shardCount} shards..."

        // Start with a new, empty report
        def finalReport = new PolicyReport()

        for (int i = 0; i < shardCount; i++) {
            def reportPath = "${workspace.getShardDirectory(i)}/report.yaml"

            if (steps.fileExists(reportPath)) {
                steps.echo "Found report at ${reportPath}, merging..."
                def partialYaml = steps.readYaml(file: reportPath)

                if (partialYaml) { // Ensure file is not empty
                    def partialReport = new PolicyReport(partialYaml)
                    finalReport.merge(partialReport)
                }
            } else {
                steps.echo "No report file found at ${reportPath}, skipping."
            }
        }

        steps.echo "Merge complete. Final summary: ${finalReport.summary}"
        return finalReport
    }
}