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
                    # 1. CORRECT SYNTAX: awk program is in single quotes (\') and uses \$1.
                    hash=\$(sha256sum "\$file" | awk \'{print \$1}\')
        
                    # 2. ROBUSTNESS CHECK: Handles cases where hash calculation fails.
                    if [ -z "\$hash" ]; then
                        echo "Warning: Could not compute hash for file: \$file. Skipping." >&2
                        continue
                    fi
        
                    # The rest of the script will now execute safely
                    hash_dec=\$((0x\${hash:0:8}))
                    target_index=\$((hash_dec % ${config.parallelStageCount}))
                    destination_dir="${workspace.getShardsBaseDirectory()}/\${target_index}"
                    cp "\$file" "\$destination_dir/"
                done
        
                echo "Bulk file distribution complete."
            '
        """)
    }
}