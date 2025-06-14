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

class FileDistributor {

	private final WorkspaceManager workspace
	private final Configuration config
	private final def steps

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

		steps.echo "Distributing files from '${sourceDir}' into ${parallelStageCount} shards based on file content..."

		steps.sh(script: """
            set -e
    
            find "${sourceDir}" -type f | while IFS= read -r file; do
                hash=\$(sha256sum "\$file" | awk '{print \$1}')
                if [ -z "\$hash" ]; then
                    echo "Warning: Could not compute hash for file: \$file. Skipping." >&2
                    continue
                fi
                hash_prefix=\$(echo "\$hash" | cut -c1-8)
                hash_dec=\$((0x\$hash_prefix))
                target_index=\$((hash_dec % ${parallelStageCount}))
                destination_dir="${shardsBasePath}/\${target_index}"
                cp "\$file" "\$destination_dir/"
            done
        
            echo "Bulk file distribution complete."
        """)
	}
}