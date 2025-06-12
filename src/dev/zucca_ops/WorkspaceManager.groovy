package dev.zucca_ops

class WorkspaceManager {

    // The root directory for all temporary files for this run.
    private final String workspaceRoot

    // Use constants for subdirectory names for easy modification.
    private static final String SHARDS_DIR_NAME = 'shards'
    private static final String RESULTS_DIR_NAME = 'results'

    /**
     * @param baseDirectory The base path for the workspace, typically the result of pwd()
     * @param runIdentifier A unique ID for the run, like BUILD_NUMBER, to avoid collisions.
     */
    WorkspaceManager(String baseDirectory, String runIdentifier) {
        // Create a unique root directory for this specific run, e.g., '.workspace/run-42'
        this.workspaceRoot = "${baseDirectory}/.workspace/run-${runIdentifier}"
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
     * Returns the path to the directory where final results should be stored.
     */
    String getResultDirectory() {
        return "${this.workspaceRoot}/${RESULTS_DIR_NAME}"
    }

    String getFolder(String folder) {
        if (folder.startsWith("/")) {
            return folder
        }

        return "${this.workspaceRoot}/${folder}"
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
}