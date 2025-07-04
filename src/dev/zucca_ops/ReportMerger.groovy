/*
 * Copyright 2025 GuidoZuccarelli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.zucca_ops

/**
 * Merges the partial Kyverno Policy Reports generated by each parallel stage
 * into a single, aggregated report. It is responsible for reading the raw
 * output from each shard, cleaning it, parsing it, and combining the results.
 */
class ReportMerger implements Serializable {

	private final def workspace
	private final def steps

	/**
	 * @param workspace A WorkspaceManager object that provides paths to shard directories.
	 * @param steps The Jenkins pipeline steps provider, used to call file system operations.
	 */
	ReportMerger(def workspace, def steps) {
		this.workspace = workspace
		this.steps = steps
	}

	/**
	 * Finds all partial reports from shard directories, cleans them, parses them, and merges them.
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
	 * Private helper to clean the raw string output from the Kyverno CLI. It finds
	 * the start of the YAML document ('apiVersion:') and parses only that portion,
	 * ignoring any preceding log lines.
	 * @param rawOutput The raw string content from a report file.
	 * @return A clean PolicyReport object, or null if no valid report can be parsed.
	 */
	private PolicyReport parseCleanReportFrom(String rawOutput) {
		if (!rawOutput || rawOutput.trim().isEmpty()) {
			return null
		}

		def reportStartIndex = rawOutput.indexOf("apiVersion")
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