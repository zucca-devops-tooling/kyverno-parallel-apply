package dev.zucca_ops
import spock.lang.Specification

class ConfigurationSpec extends Specification {

    // Default config
    def steps = new Expando(
            fileExists: { true },
            readYaml: { [:] },
            echo: { },
            error: { throw new RuntimeException(it) }
    )

    def "loads defaults when no configFile is provided and no overrides"() {
        given:
        def config = new Configuration([:], steps)

        when:
        config.loadConfig()

        then:
        config.policyPath == './policies'
        config.finalReportPath == 'final-kyverno-report.yaml'
        config.valuesFilePath == null
        config.parallelStageCount == 4
        config.manifestSourceDirectory == './kustomize-output'
        config.extraKyvernoArgs == ''
        config.generatedResourcesDir == 'generated-resources'
        config.kyvernoVerbosity == 2
        config.debugLogDir == null
    }

    def "overrides default values with params"() {
        given:
        def params = [
                policyPath: './my-policies',
                finalReportPath: 'my-report.yaml',
                parallelStageCount: 8,
                manifestSourceDirectory: './manifests',
                extraKyvernoArgs: '--client',
                generatedResourcesDir: 'output-gen',
                kyvernoVerbosity: 5,
                debugLogDir: '/tmp/debug'
        ]
        def config = new Configuration(params, steps)

        when:
        config.loadConfig()

        then:
        config.policyPath == './my-policies'
        config.finalReportPath == 'my-report.yaml'
        config.parallelStageCount == 8
        config.manifestSourceDirectory == './manifests'
        config.extraKyvernoArgs == '--client'
        config.generatedResourcesDir == 'output-gen'
        config.kyvernoVerbosity == 5
        config.debugLogDir == '/tmp/debug'
    }

    def "overrides values from configFile when provided"() {
        given:
        steps.readYaml = { [policyPath: './from-file', parallelStageCount: 3] }
        def params = [configFile: 'config.yaml']
        def config = new Configuration(params, steps)

        when:
        config.loadConfig()

        then:
        config.policyPath == './from-file'
        config.parallelStageCount == 3
    }

    def "params override configFile values when both are present"() {
        given:
        steps.readYaml = { [policyPath: './from-file', finalReportPath: 'report-from-file.yaml'] }

        def params = [
                configFile: 'config.yaml',
                policyPath: './from-params'
        ]
        def config = new Configuration(params, steps)

        when:
        config.loadConfig()

        then:
        config.policyPath == './from-params'
        config.finalReportPath == 'report-from-file.yaml'
    }

    def "fails validation when parallelStageCount is zero or negative"() {
        given:
        steps.error = { String msg -> throw new RuntimeException(msg) }
        def config = new Configuration([parallelStageCount: 0], steps)

        when:
        config.loadConfig()

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("parallelStageCount")
    }

    def "fails validation when manifestSourceDirectory does not exist"() {
        given:
        steps.error = { String msg -> throw new RuntimeException(msg) }
        steps.fileExists = { path -> (path != './kustomize-output') }

        def config = new Configuration([:], steps)

        when:
        config.loadConfig()

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("manifestSourceDirectory")
    }

    def "fails validation when extraKyvernoArgs contains forbidden flags"() {
        given:
        steps.error = { String msg -> throw new RuntimeException(msg) }
        def config = new Configuration([extraKyvernoArgs: "--debug -o yaml"], steps)

        when:
        config.loadConfig()

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("forbidden flag")
    }

    def "fails validation when valuesFilePath is set but file does not exist"() {
        given:
        steps.error = { String msg -> throw new RuntimeException(msg) }
        steps.fileExists = { path -> path != 'my-values.yaml' }

        def config = new Configuration([valuesFilePath: 'my-values.yaml'], steps)

        when:
        config.loadConfig()

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("valuesFilePath")
    }
}