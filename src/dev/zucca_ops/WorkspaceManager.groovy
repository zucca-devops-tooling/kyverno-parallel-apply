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
 * Manages the temporary directory structure used during the parallel execution.
 * This class is the single source of truth for all temporary paths, ensuring
 * that file operations are consistent and contained within a unique, cleanable workspace.
 */
class WorkspaceManager implements Serializable {

	// The root directory for all temporary files for this run.
	private final String workspaceRoot

	// The absolute path of the Jenkins workspace, used for path calculations.
	private final String baseDirectory

	// Use constants for subdirectory names for easy modification.
	private static final String SHARDS_DIR_NAME = 'shards'
	private static final String RESULTS_DIR_NAME = 'results'

	/**
	 * @param baseDirectory The base path for the workspace, typically the result of pwd()
	 * @param runIdentifier A unique ID for the run, like BUILD_NUMBER, to avoid collisions.
	 */
	WorkspaceManager(String baseDirectory, String runIdentifier) {
		this.baseDirectory = baseDirectory
		// Create a unique root directory for this specific run, e.g., '.workspace/run-42'
		this.workspaceRoot = "${baseDirectory}/.workspace/run-${runIdentifier}"
	}

	/**
	 * Returns the absolute path to the root of the temporary workspace.
	 */
	String getWorkspacePath() {
		return this.workspaceRoot
	}

	/**
	 * Returns the path to the directory that will hold all parallel shard folders.
	 */
	String getShardsBaseDirectory() {
		return "${this.workspaceRoot}/${SHARDS_DIR_NAME}"
	}

	/**
	 * Returns the path for a specific shard directory, e.g., './.workspace/run-42/shards/0'
	 * This is the method your other functions will call.
	 */
	String getShardDirectory(int index) {
		return "${getShardsBaseDirectory()}/${index}"
	}

	/**
	 * Returns the absolute path for a specific shard's debug log file.
	 * It intelligently handles both relative and absolute paths provided
	 * by the user in the configuration.
	 *
	 * @param debugLogDir The user-configured directory for debug logs.
	 * @param index The index of the shard (e.g., 0, 1, 2...).
	 * @return The full, absolute path to the log file.
	 */
	String getShardLogFile(String debugLogDir, int index) {
		String filename = "shard-${index}-debug.log"

		// The simplest and safest way to check for an absolute path in this context
		if (isAbsolutePath(debugLogDir)) {
			// Path is already absolute, just append the filename
			return "${debugLogDir}/${filename}"
		} else {
			// Path is relative, prepend the known absolute path to the workspace root
			return "${this.baseDirectory}/${debugLogDir}/${filename}"
		}
	}

	/**
	 * Returns the path to the directory where final results should be stored.
	 */
	String getResultDirectory() {
		return "${this.workspaceRoot}/${RESULTS_DIR_NAME}"
	}

	/**
	 * Converts a potentially relative path into a full, absolute path based
	 * on the Jenkins workspace directory. If the path is already absolute,
	 * it is returned unchanged.
	 * @param folder The path to make absolute.
	 * @return The absolute path as a String.
	 */
	String getFolder(String folder) {
		if (isAbsolutePath(folder)) {
			return folder
		}
		return "${this.baseDirectory}/${folder}"
	}

	/**
	 * Creates the entire directory structure needed for the run.
	 * @param steps The pipeline steps object to execute 'sh'.
	 */
	void createDirectories(def steps, int shardCount) {
		steps.echo "Setting up temporary workspace at: ${this.workspaceRoot}"
		// Create the base directory for all shards
		steps.sh "mkdir -p ${getShardsBaseDirectory()}"
		// Create each individual shard directory
		for (int i = 0; i < shardCount; i++) {
			steps.sh "mkdir -p ${getShardDirectory(i)}"
		}
		// Create the results directory
		steps.sh "mkdir -p ${getResultDirectory()}"
	}

	/**
	 * Cleans up the entire temporary workspace.
	 * @param steps The pipeline steps object to execute 'sh'.
	 */
	void cleanup(def steps) {
		steps.echo "Cleaning up temporary workspace: ${this.workspaceRoot}"
		// Safely remove the entire workspace root directory
		steps.sh "rm -rf ${this.workspaceRoot}"
	}

	/**
	 * Converts an absolute path into a path relative to the Jenkins workspace root.
	 * This is required for steps like 'archiveArtifacts'.
	 * @param absolutePath The full path to a file or directory.
	 * @return The path relative to the workspace.
	 */
	String getRelativePath(String absolutePath) {
		return absolutePath.replaceFirst("^${this.baseDirectory}/", "")
	}

	/**
	 * Private helper to check if a path string is absolute.
	 */
	private static boolean isAbsolutePath(String path) {
		return path != null && path.startsWith("/")
	}
}