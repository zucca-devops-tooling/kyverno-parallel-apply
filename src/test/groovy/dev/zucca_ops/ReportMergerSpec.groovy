package dev.zucca_ops

import spock.lang.Specification

class ReportMergerSpec extends Specification {

	// A default mock 'steps' object for our tests.
	// Individual tests can override its methods as needed.
	def steps = new Expando(
	fileExists: { def path -> true },
	echo: { String msg -> println msg },
	error: { throw new RuntimeException(it) },
	readYaml: { Map args -> [:] },
	readFile: { def path ->
		""
	} // Default to returning an empty string
	)

	// A default mock for the WorkspaceManager.
	def workspace = new Expando(
	getShardDirectory: { int index ->
		return "shard-${index}"
	}
	)

	def "merges multiple valid partial reports"() {
		given: "We mock fileExists to only find reports in the first two shards"
		steps.fileExists = { String path -> path.contains("shard-0") || path.contains("shard-1") }

		and: "We mock readFile to return the raw string content for each shard's report"
		steps.readFile = { String path ->
			if (path.contains("shard-0")) return "log line\napiVersion: v1\nsummary:\n  pass: 10\nresults:\n- {policy: 'policy-a'}"
			if (path.contains("shard-1")) return "log line\napiVersion: v1\nsummary:\n  fail: 5\nresults:\n- {policy: 'policy-b'}"
			return ""
		}

		and: "We mock readYaml to parse the cleaned text passed from the 'parseCleanReportFrom' method"
		steps.readYaml = { Map args ->
			def text = args.text
			if (text.contains("pass: 10")) return [apiVersion: 'v1', summary: [pass: 10], results: [[policy: 'policy-a']]]
			if (text.contains("fail: 5")) return [apiVersion: 'v1', summary: [fail: 5], results: [[policy: 'policy-b']]]
			return [:]
		}

		def merger = new ReportMerger(workspace, steps)

		when: "We merge reports from 3 shards (one of which won't be found)"
		def report = merger.merge(3)

		then: "The summaries are aggregated and the results are combined"
		report.summary.pass == 10
		report.summary.fail == 5
		report.results.size() == 2
	}


	def "skips non-existent report files"() {
		given:
		steps.fileExists = { false }
		def merger = new ReportMerger(workspace, steps)

		when:
		def report = merger.merge(2)

		then: "The final report remains empty"
		report.summary == [pass: 0, fail: 0, warn: 0, error: 0, skip: 0]
		report.results.isEmpty()
	}

	def "handles a report that is missing the 'summary' block"() {
		given:
		// 1. Mock readFile to return the raw string for this scenario.
		steps.readFile = { "apiVersion: v1\nkind: ClusterPolicyReport\nresults:\n- {policy: 'policy-d'}" }

		// 2. Mock readYaml to return the parsed map, which has no summary.
		def reportWithoutSummary = [
			apiVersion: 'v1',
			kind: 'ClusterPolicyReport',
			results: [[policy: 'policy-d']]
		]
		steps.readYaml = { Map args -> return reportWithoutSummary }

		def merger = new ReportMerger(workspace, steps)

		when:
		def finalReport = merger.merge(1)

		then: "The merge completes, results are added, and summary counts remain 0"
		noExceptionThrown()
		finalReport.results.size() == 1
		finalReport.results[0].policy == 'policy-d'
		finalReport.summary.pass == 0
		finalReport.summary.fail == 0
	}

	def "handles a report that is missing the 'results' list"() {
		given:
		// 1. Mock readFile to return the raw string content for the report.
		def rawYamlString = "apiVersion: v1\nkind: ClusterPolicyReport\nsummary:\n  pass: 5"
		steps.readFile = { return rawYamlString }

		// 2. Mock readYaml to correctly parse the cleaned YAML text.
		def parsedData = [
			apiVersion: 'v1',
			kind: 'ClusterPolicyReport',
			summary: [pass: 5]
		]
		steps.readYaml = { Map args ->
			// The parseCleanReportFrom method will pass the cleaned string as 'text'.
			if (args.text.contains("pass: 5")) {
				return parsedData
			}
			return [:]
		}
		def merger = new ReportMerger(workspace, steps)

		when:
		def finalReport = merger.merge(1)

		then: "The merge completes without error and the summary is updated"
		noExceptionThrown()
		finalReport.summary.pass == 5
		finalReport.results.isEmpty()
	}

	def "handles being called with zero shards"() {
		given:
		def merger = new ReportMerger(workspace, steps)

		when:
		def report = merger.merge(0)

		then: "A clean, default report is returned"
		report.summary == [pass: 0, fail: 0, warn: 0, error: 0, skip: 0]
		report.results.isEmpty()
	}

	def "handles a partial report that is valid YAML but an empty map"() {
		given:
		// Simulate readYaml returning an empty map, e.g., from a file with just "---".
		steps.readYaml = { [:] }
		def merger = new ReportMerger(workspace, steps)

		when:
		def report = merger.merge(1)

		then: "The merge completes without error and the final report is empty"
		noExceptionThrown()
		report.summary.pass == 0
		report.results.isEmpty()
	}
}
