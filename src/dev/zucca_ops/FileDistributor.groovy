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
 * Handles the efficient distribution of manifest files into multiple shard
 * directories for parallel processing. It uses a content-based hash to ensure
 * an even distribution and performs all file operations in a single, high-performance
 * shell script to minimize overhead.
 */
class FileDistributor {

	private final WorkspaceManager workspace
	private final Configuration config
	private final def steps

	/**
	 * @param workspace A WorkspaceManager object that provides paths to shard directories.
	 * @param config The fully resolved Configuration object for this run.
	 * @param steps The Jenkins pipeline steps provider, used to execute commands.
	 */
	FileDistributor(WorkspaceManager workspace, Configuration config, def steps) {
		this.workspace = workspace
		this.config = config
		this.steps = steps
	}

	/**
	 * This method uses a highly efficient, single shell command to distribute files.
	 * It hashes the *content* of each file to ensure an even, non-clustered distribution.
	 */
	void distribute() {
		def sourceDir = config.manifestSourceDirectory
		def shardsBasePath = workspace.getShardsBaseDirectory()
		def parallelStageCount = config.parallelStageCount

		// If there's only one shard, distribution is unnecessary.
		if (parallelStageCount <= 1) {
			return
		}

		steps.echo "Distributing files from '${sourceDir}' into ${parallelStageCount} shards based on file content..."

		steps.sh(script: """
            set -e
    
            find "${sourceDir}" -type f | while IFS= read -r file; do

                # Use 'sha256sum' to get a hash of the file's CONTENT.
                hash=\$(sha256sum "\$file" | awk '{print \$1}')
                
                if [ -z "\$hash" ]; then
                    echo "Warning: Could not compute hash for file: \$file. Skipping." >&2
                    continue
                fi
                
                 # Use 'cut' to get the first 8 characters. This is POSIX-compliant.
                hash_prefix=\$(echo "\$hash" | cut -c1-8)
                
                # Convert the hex prefix to a decimal number.
                hash_dec=\$((0x\$hash_prefix))
                
                # Use shell arithmetic to calculate the target shard index.
                target_index=\$((hash_dec % ${parallelStageCount}))

				# Construct the destination path and copy the file.
                destination_dir="${shardsBasePath}/\${target_index}"
                cp "\$file" "\$destination_dir/"
            done
        
            echo "Bulk file distribution complete."
        """)
	}
}