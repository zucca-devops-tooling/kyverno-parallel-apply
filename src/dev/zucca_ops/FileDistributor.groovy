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
        def shardsBasePath = workspace.getShardsBaseDirectory()
        def parallelStageCount = config.parallelStageCount

        steps.echo "Distributing files from '${sourceDir}' into ${parallelStageCount} shards based on file content..."

        def bulkDistributeScript = """
        #!/bin/bash
        set -e

        find "${sourceDir}" -type f | while IFS= read -r file; do
            # Get sha256 hash of the file content
            hash=\$(sha256sum "\${file}" | awk '{print \$1}')
            
            # Take first 8 hex chars, convert to decimal
            hash_dec=\$((0x\${hash:0:8}))
            
            # Determine shard index
            target_index=\$((hash_dec % ${parallelStageCount}))
            
            # Compute destination dir
            destination_dir="${shardsBasePath}/\${target_index}"

            # Actually copy the file
            cp "\${file}" "\${destination_dir}/"
        done

        echo "Bulk file distribution complete."
    """

        steps.sh(script: bulkDistributeScript)
    }
}