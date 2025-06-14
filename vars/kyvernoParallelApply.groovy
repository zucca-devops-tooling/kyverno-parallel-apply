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

import dev.zucca_ops.Configuration
import dev.zucca_ops.FileDistributor
import dev.zucca_ops.KyvernoRunner
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
							KyvernoRunner runner = new KyvernoRunner(config, workspace, shardIndex, this)
							return runner.run()
						}
					}
				}
			}

			stageResults = parallel(parallelStages)

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
			archiveArtifacts artifacts: workspace.getRelativePath(finalReportPath), followSymlinks: false
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