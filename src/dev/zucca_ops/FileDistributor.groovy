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

        steps.sh(script: """
            bash -c '
                set -e
        
                find "${config.manifestSourceDirectory}" -type f | while IFS= read -r file; do
                    # Get sha256 hash of the file content
                    hash=\$(sha256sum "\$file" | awk \'{print \$1}\')
        
                    # Extract first 8 hex chars and convert to decimal
                    hash_dec=\$((0x\${hash:0:8}))
        
                    # Compute shard index
                    target_index=\$((hash_dec % ${config.parallelStageCount}))
        
                    # Get target dir from Groovy
                    destination_dir="${workspace.getShardsBaseDirectory()}/\${target_index}"
        
                    # Copy the file
                    cp "\$file" "\$destination_dir/"
                done
        
                echo "Bulk file distribution complete."
            '
        """)
    }
}