package dev.zucca_ops

import spock.lang.Specification


/**
 * Unit tests for the KyvernoRunner class.
 */
class KyvernoRunnerSpec extends Specification {

    // Mock objects that will be passed to the KyvernoRunner instance.
    def config
    def workspace
    def steps

    // A variable to capture the command string passed to the mock 'sh' step.
    String executedCommand

    // The setup() method runs before each test to ensure a clean state.
    def setup() {
        // Mock the Configuration object with default values.
        config = new Expando(
                policyPath: 'policies/',
                generatedResourcesDir: 'generated/',
                valuesFilePath: null,
                kyvernoVerbosity: 2,
                extraKyvernoArgs: "",
                debugLogDir: null
        )

        // Mock the WorkspaceManager to return predictable paths.
        workspace = new Expando(
                getShardDirectory: { int index -> "/tmp/shard-${index}" },
                getFolder: { String path -> "/abs/${path}" },
                getShardLogFile: { String dir, int index -> "/abs/${dir}/shard-${index}.log" }
        )

        // Mock the Jenkins steps object. The 'sh' command captures its input for verification.
        steps = new Expando(
                sh: { String cmd -> executedCommand = cmd },
                echo: { }
        )
    }

    def "run() constructs the correct default command"() {
        given:
        def runner = new KyvernoRunner(config, workspace, 0, steps)

        when:
        def result = runner.run()

        then: "The shell command should be built correctly with default values"
        executedCommand.contains("kyverno apply '/abs/policies/'")
        executedCommand.contains("--resource='/tmp/shard-0'")
        executedCommand.contains("-v 2")
        executedCommand.contains("-o /abs/generated/")
        executedCommand.contains("--policy-report")

        and: "Optional flags should NOT be present"
        !executedCommand.contains("--values-file")
        !executedCommand.contains("2>") // No stderr redirection

        and: "The method should return a success status"
        result.status == 'SUCCESS'
    }

    def "run() correctly includes optional flags when configured"() {
        given: "We override the config with all optional parameters set"
        config.valuesFilePath = 'values.yaml'
        config.extraKyvernoArgs = '--cluster --audit-warn'
        config.debugLogDir = 'debug/'

        def runner = new KyvernoRunner(config, workspace, 1, steps)

        when:
        runner.run()

        then: "The shell command should contain all the additional flags"
        executedCommand.contains("--values-file '/abs/values.yaml'")
        executedCommand.contains("--cluster --audit-warn")
        executedCommand.contains("2> '/abs/debug//shard-1.log'")
    }

    def "run() returns a failure map when sh command throws an exception"() {
        given: "We configure the mock 'sh' step to throw an error"
        steps.sh = { String cmd -> throw new RuntimeException("command failed!") }
        def runner = new KyvernoRunner(config, workspace, 2, steps)

        when:
        def result = runner.run()

        then: "The method should catch the exception and return a failure status"
        noExceptionThrown() // The run() method itself should not throw.
        result.status == 'FAILURE'
        result.error.contains("command failed!")
    }
}
