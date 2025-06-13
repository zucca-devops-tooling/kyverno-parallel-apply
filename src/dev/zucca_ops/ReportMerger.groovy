package dev.zucca_ops

class ReportMerger implements Serializable {

    private final WorkspaceManager workspace
    private final def steps

    ReportMerger(WorkspaceManager workspace, def steps) {
        this.workspace = workspace
        this.steps = steps
    }

    /**
     * Finds all partial reports, cleans them, parses them, and merges them.
     * @param shardCount The number of shards to check for reports.
     * @return A single, merged PolicyReport object.
     */
    PolicyReport merge(int shardCount) {
        steps.echo "Starting merge process for ${shardCount} shards..."
        def finalReport = new PolicyReport()

        for (int i = 0; i < shardCount; i++) {
            def reportPath = "${workspace.getShardDirectory(i)}/report.yaml"

            if (steps.fileExists(reportPath)) {
                steps.echo "Processing report at ${reportPath}..."

                // Read the entire raw output file, including any log lines.
                def rawContent = steps.readFile(reportPath)

                println(rawContent)

                // Use the helper method to clean and parse the content.
                def partialReport = parseCleanReportFrom(rawContent)

                if (partialReport) {
                    finalReport.merge(partialReport)
                } else {
                    steps.echo "WARNING: No valid PolicyReport found in raw output for Shard ${i}."
                }
            } else {
                steps.echo "No report file found at ${reportPath}, skipping."
            }
        }

        steps.echo "Merge complete. Final summary: ${finalReport.summary}"
        return finalReport
    }

    /**
     * --- THIS IS THE CORRECT LOGIC ---
     * Private helper to clean the raw string output from the Kyverno CLI.
     * It finds the start of the YAML document and parses only that portion.
     * @param rawOutput The raw string containing logs and the YAML report.
     * @return A clean PolicyReport object, or null if no report is found.
     */
    private PolicyReport parseCleanReportFrom(String rawOutput) {
        if (!rawOutput || rawOutput.trim().isEmpty()) {
            return null
        }

        def reportStartIndex = rawOutput.indexOf("apiVersion:")
        if (reportStartIndex != -1) {
            def cleanReportYaml = rawOutput.substring(reportStartIndex)
            try {
                def parsedData = steps.readYaml(text: cleanReportYaml)
                // Ensure we got a valid map before creating the object
                if (parsedData instanceof Map) {
                    return new PolicyReport(parsedData)
                }
            } catch (Exception e) {
                steps.echo "ERROR: Failed to parse cleaned YAML. Error: ${e.message}"
                return null
            }
        }

        // Return null if 'apiVersion:' was not found in the output
        return null
    }
}