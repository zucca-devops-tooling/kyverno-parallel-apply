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
     * Finds files in the source directory and distributes them into the shard
     * directories managed by the WorkspaceManager.
     */
    void distribute() {
        def sourceDir = config.manifestSourceDirectory
        steps.echo "Distributing files from '${sourceDir}' into ${config.parallelStageCount} shards..."

        def files = steps.findFiles(glob: "${sourceDir}/**/*")

        files.each { file ->
            // Using the file's relative path for the hash is efficient and deterministic.
            byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(file.path.getBytes('UTF-8'))

            // Convert first 4 bytes of hash to a positive integer
            long hashInt = ((hashBytes[0] & 0xFF) << 24) |
                    ((hashBytes[1] & 0xFF) << 16) |
                    ((hashBytes[2] & 0xFF) << 8)  |
                    ((hashBytes[3] & 0xFF))
            hashInt = hashInt & 0xFFFFFFFFL

            int targetIndex = (int) (hashInt % config.parallelStageCount)

            // Ask the workspace manager for the correct destination folder
            def targetDir = workspace.getShardDirectory(targetIndex)
            def destinationPath = "${targetDir}/${file.name}"

            // Use built-in, platform-independent steps to copy the file
            def content = steps.readFile(file.path)
            steps.writeFile(file: destinationPath, text: content)
        }
        steps.echo "File distribution complete."
    }
}