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
 * Manages all user-provided and default parameters for the library.
 * It resolves the final configuration by layering defaults, values from a
 * config file, and direct parameters. It also performs upfront validation
 * to ensure the library is correctly configured before execution.
 */
class Configuration implements Serializable {

	// --- Properties to hold the final resolved configuration ---
	String policyPath
	String finalReportPath
	String valuesFilePath
	int parallelStageCount
	String manifestSourceDirectory
	String extraKyvernoArgs
	String generatedResourcesDir
	int kyvernoVerbosity
	String debugLogDir
	boolean failFast // Added based on earlier discussion

	// These properties hold the initial state passed to the constructor.
	final Map paramsConfig
	final def steps

	// Defines all default values for the configuration parameters.
	private static final Map DEFAULTS = [
			policyPath: './policies',
			finalReportPath: 'final-kyverno-report.yaml',
			valuesFilePath: null,
			parallelStageCount: 4,
			manifestSourceDirectory: './kustomize-output',
			kyvernoVerbosity: 2,
			generatedResourcesDir: 'generated-resources',
			extraKyvernoArgs: "",
			debugLogDir: null,
			failFast: false
	].asImmutable()

	// Defines a list of arguments that are forbidden from being passed via extraKyvernoArgs.
	private static final List FORBIDDEN_ARGS = [
			'-o',
			'--output' // These flags change the output format and will break report merging.
	]

	/**
	 * Constructor that stores the initial parameters and the Jenkins steps provider.
	 * @param params The map of parameters passed from the main library call.
	 * @param steps A reference to the pipeline steps provider for file I/O and other operations.
	 */
	Configuration(Map params, def steps) {
		this.steps = steps
		this.paramsConfig = params
	}

	/**
	 * Loads and resolves the final configuration. This method applies defaults,
	 * merges values from a user-specified YAML config file, and then merges
	 * any direct parameters, which have the highest precedence. Finally, it
	 * triggers validation.
	 */
	void loadConfig() {
		def effectiveConfig = new HashMap(DEFAULTS)

		if (paramsConfig.configFile) {
			steps.echo "Reading configuration from ${paramsConfig.configFile}"
			// This check should happen in validate() to ensure steps are called safely
			Map configFromFile = steps.readYaml(file: paramsConfig.configFile)
			if (configFromFile) {
				effectiveConfig.putAll(configFromFile)
			}
		}

		effectiveConfig.putAll(paramsConfig)

		// Assign all properties from the final effective configuration map.
		this.policyPath = effectiveConfig.policyPath
		this.finalReportPath = effectiveConfig.finalReportPath
		this.valuesFilePath = effectiveConfig.valuesFilePath
		this.parallelStageCount = effectiveConfig.parallelStageCount as int
		this.manifestSourceDirectory = effectiveConfig.manifestSourceDirectory
		this.extraKyvernoArgs = effectiveConfig.extraKyvernoArgs ?: "" // Ensure not null
		this.generatedResourcesDir = effectiveConfig.generatedResourcesDir
		this.kyvernoVerbosity = effectiveConfig.kyvernoVerbosity as int
		this.debugLogDir = effectiveConfig.debugLogDir
		this.failFast = effectiveConfig.failFast as boolean

		validate()
	}

	/**
	 * Validates the final state of the configuration to ensure the library
	 * can run safely. It checks for the existence of required files/directories
	 * and ensures parameter values are within valid ranges. Throws a build-stopping
	 * error if any validation fails.
	 */
	private void validate() {
		steps.echo "Validating configuration..."

		if (paramsConfig.configFile && !steps.fileExists(paramsConfig.configFile)) {
			steps.error("Configuration Error: The specified configFile '${paramsConfig.configFile}' does not exist.")
		}

		if (this.parallelStageCount <= 0) {
			steps.error("Configuration Error: 'parallelStageCount' must be a positive number, but got '${this.parallelStageCount}'.")
		}

		if (!steps.fileExists(this.manifestSourceDirectory)) {
			steps.error("Configuration Error: The 'manifestSourceDirectory' you provided at '${this.manifestSourceDirectory}' does not exist.")
		}

		if (!steps.fileExists(this.policyPath)) {
			steps.error("Configuration Error: The 'policyPath' you provided at '${this.policyPath}' does not exist.")
		}

		if (this.valuesFilePath && !steps.fileExists(this.valuesFilePath)) {
			steps.error("Configuration Error: The 'valuesFilePath' you provided at '${this.valuesFilePath}' does not exist.")
		}

		// Validate the extraKyvernoArgs to prevent dangerous flags
		if (this.extraKyvernoArgs) {
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