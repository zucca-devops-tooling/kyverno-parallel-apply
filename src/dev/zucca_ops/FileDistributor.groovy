package dev.zucca_ops
import java.security.MessageDigest


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
        steps.echo "Distributing files from '${sourceDir}' into ${config.parallelStageCount} shards based on file content..."

        // --- THIS IS THE FIX ---
        // 1. Get the absolute base path for all shard directories from the WorkspaceManager.
        //    This is done once, outside the script.
        def shardsBasePath = workspace.getShardsBaseDirectory()

        // 2. This single, multi-line shell script does all the work efficiently.
        //    It avoids the overhead of calling Jenkins steps in a loop.
        def bulkDistributeScript = """
            #!/bin/bash
            set -e

            # Use 'find' to get a list of all files to be processed.
            find "${sourceDir}" -type f | while IFS= read -r file; do
                # Use 'sha256sum' to get a hash of the file's CONTENT.
                hash=\$(sha256sum "\$file" | awk '{print \$1}')
                
                # Convert the first 8 hex characters of the hash to a decimal number.
                hash_dec=\$((0x\${hash:0:8}))
                
                # Use shell arithmetic to calculate the target shard index.
                target_index=\$((hash_dec % ${config.parallelStageCount}))
                
                # Construct the destination path using the pre-calculated base path.
                # This is much cleaner and more correct than the previous version.
                destination_dir="${shardsBasePath}/\${target_index}"

                cp "\$file" "\$destination_dir/"
            done

            echo "Bulk file distribution complete."
        """
        // --- END OF FIX ---

        // Execute the entire distribution logic in one go.
        steps.sh(bulkDistributeScript)
    }
}