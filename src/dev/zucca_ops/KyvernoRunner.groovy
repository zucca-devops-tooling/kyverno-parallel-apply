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
 * This class is responsible for running the 'kyverno apply' command for a single shard.
 * It encapsulates all the logic for command construction and execution, based on the
 * user's excellent design. It is designed to be instantiated and called from within a
 * parallel stage in a Jenkins Pipeline.
 */
class KyvernoRunner implements Serializable {

	private final def config
	private final def workspace
	private final int shardIndex
	private final def steps

	/**
	 * Constructor for the KyvernoRunner.
	 * @param config The fully resolved Configuration object for this run.
	 * @param workspace The WorkspaceManager object that provides all necessary paths.
	 * @param shardIndex The integer index of the parallel shard this runner is responsible for.
	 * @param steps The Jenkins pipeline steps provider, used to execute commands like 'sh'.
	 */
	KyvernoRunner(def config, def workspace, int shardIndex, def steps) {
		this.config = config
		this.workspace = workspace
		this.shardIndex = shardIndex
		this.steps = steps
	}

	/**
	 * Executes the 'kyverno apply' command for the configured shard. It builds the
	 * command string with all user-configured options, executes it, redirects
	 * output and error streams, and returns a map indicating the success or failure
	 * of the operation.
	 * @return A Map with a 'status' key ('SUCCESS' or 'FAILURE') and an optional 'error' message.
	 */
	Map run() {
		try {
			def shardDir = config.parallelStageCount > 1 ? workspace.getShardDirectory(shardIndex) : config.manifestSourceDirectory
			def policyPath = workspace.getFolder(config.policyPath)
			def generatedResourcesDir = workspace.getFolder(config.generatedResourcesDir)

			def valuesFileCommand = config.valuesFilePath != null
					? " --values-file '${workspace.getFolder(config.valuesFilePath)}'"
					: ""

			def stdErrRedirect = config.debugLogDir != null
					? " 2> '${workspace.getShardLogFile(config.debugLogDir, shardIndex)}'"
					: ""

			// Build the command parts exactly as specified by the user.
			def commandParts = [
				"kyverno",
				"apply",
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

			// Execute the final command. If this sh step fails, the try/catch block will handle it.
			steps.sh "${command} ${reportOutput} ${stdErrRedirect}"

			return [status: 'SUCCESS']
		} catch (Exception e) {
			// If sh() fails, the exception is caught here.
			steps.echo "ERROR: Shard ${shardIndex} failed!"
			return [status: 'FAILURE', error: e.message]
		}
	}
}