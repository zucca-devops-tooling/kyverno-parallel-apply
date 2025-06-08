package dev.zucca_ops

class Configuration {

    final String policyPath
    final String finalReportPath
    final String valuesFilePath
    final int parallelStageCount
    final String manifestSourceDirectory
    final String extraKyvernoArgs

    private static final Map DEFAULTS = [
            policyPath: './policies',
            finalReportPath: 'final-kyverno-report.yaml',
            valuesFilePath: null,
            parallelStageCount: 4,
            manifestSourceDirectory: './kustomize-output'
    ].asImmutable()

    private static final List FORBIDDEN_ARGS = [
            '-o', '--output' // These flags change the output format and will break report merging.
    ]

    /**
     * Constructor that resolves the final configuration.
     * @param params The map of parameters passed to the pipeline step.
     * @param steps A reference to the pipeline steps object for file I/O.
     */
    Configuration(Map params, def steps) {
        def effectiveConfig = new HashMap(DEFAULTS)

        if (params.configFile) {
            steps.echo "Reading configuration from ${params.configFile}"
            Map configFromFile = steps.readYaml(file: params.configFile)
            if (configFromFile) {
                effectiveConfig.putAll(configFromFile)
            }
        }

        effectiveConfig.putAll(params)

        this.policyPath = effectiveConfig.policyPath
        this.finalReportPath = effectiveConfig.finalReportPath
        this.valuesFilePath = effectiveConfig.valuesFilePath
        this.parallelStageCount = effectiveConfig.parallelStageCount as int
        this.manifestSourceDirectory = effectiveConfig.manifestSourceDirectory
        this.extraKyvernoArgs = effectiveConfig.extraKyvernoArgs

        validate(steps)
    }

    /**
     * Private helper method to validate the state of the configuration.
     * Throws a build-stopping error if any validation fails.
     * @param steps The pipeline steps object to call fileExists() and error().
     */
    private void validate(def steps) {

        // 1. Validate parallelStageCount
        if (this.parallelStageCount <= 0) {
            steps.error("Configuration Error: 'parallelStageCount' must be a positive number, but got '${this.parallelStageCount}'.")
        }

        // 2. Validate manifestSourceDirectory
        if (!steps.fileExists(this.manifestSourceDirectory)) {
            steps.error("Configuration Error: The 'manifestSourceDirectory' you provided at '${this.manifestSourceDirectory}' does not exist.")
        }

        // 3. Validate policyPath
        if (!steps.fileExists(this.policyPath)) {
            steps.error("Configuration Error: The 'policyPath' you provided at '${this.policyPath}' does not exist.")
        }

        // 4. Validate valuesFilePath (only if it was provided)
        if (this.valuesFilePath) {
            if (!steps.fileExists(this.valuesFilePath)) {
                steps.error("Configuration Error: The 'valuesFilePath' you provided at '${this.valuesFilePath}' does not exist.")
            }
        }

        // 5. Validate the extraKyvernoArgs to prevent dangerous flags
        if (this.extraKyvernoArgs) {
            // Split the args string into a list of individual flags
            def providedArgs = this.extraKyvernoArgs.tokenize(' ')

            for (String forbidden : FORBIDDEN_ARGS) {
                if (forbidden in providedArgs) {
                    steps.error("Configuration Error: The 'extraKyvernoArgs' contains a forbidden flag ('${forbidden}'). Flags that alter the output format are not allowed.")
                }
            }
        }


        steps.echo "Configuration validation successful."
    }
}