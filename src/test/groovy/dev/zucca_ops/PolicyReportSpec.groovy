package dev.zucca_ops

import spock.lang.Specification

class PolicyReportSpec extends Specification {

    def "default constructor sets expected defaults"() {
        when:
        def report = new PolicyReport()

        then:
        report.apiVersion == 'wgpolicyk8s.io/v1alpha2'
        report.kind == 'ClusterPolicyReport'
        report.metadata.name.startsWith('final-merged-report-')
        report.summary == [pass: 0, fail: 0, warn: 0, error: 0, skip: 0]
        report.results == []
    }

    def "constructor from parsed YAML assigns all fields correctly"() {
        given:
        def parsed = [
                apiVersion: 'wgpolicyk8s.io/v1alpha2',
                kind: 'ClusterPolicyReport',
                metadata: [name: 'report-a'],
                summary: [pass: 2, fail: 1, warn: 0, error: 0, skip: 1],
                results: [[policy: 'foo'], [policy: 'bar']]
        ]

        when:
        def report = new PolicyReport(parsed)

        then:
        report.apiVersion == 'wgpolicyk8s.io/v1alpha2'
        report.kind == 'ClusterPolicyReport'
        report.metadata == [name: 'report-a']
        report.summary == [pass: 2, fail: 1, warn: 0, error: 0, skip: 1]
        report.results.size() == 2
    }

    def "merge aggregates summary and appends results"() {
        given:
        def r1 = new PolicyReport([
                apiVersion: 'wgpolicyk8s.io/v1alpha2',
                kind: 'ClusterPolicyReport',
                metadata: [name: 'base'],
                summary: [pass: 1, fail: 2, warn: 3, error: 4, skip: 5],
                results: [[policy: 'foo']]
        ])

        def r2 = new PolicyReport([
                apiVersion: 'wgpolicyk8s.io/v1alpha2',
                kind: 'ClusterPolicyReport',
                metadata: [name: 'extra'],
                summary: [pass: 5, fail: 4, warn: 3, error: 2, skip: 1],
                results: [[policy: 'bar']]
        ])

        when:
        r1.merge(r2)

        then:
        r1.summary == [pass: 6, fail: 6, warn: 6, error: 6, skip: 6]
        r1.results.size() == 2
        r1.results*.policy == ['foo', 'bar']
    }

    def "merge skips null or missing summary fields gracefully"() {
        given:
        def r1 = new PolicyReport()
        r1.summary = [pass: 0, fail: 0, warn: 0, error: 0, skip: 0]

        def r2 = new PolicyReport([
                apiVersion: 'wgpolicyk8s.io/v1alpha2',
                kind: 'ClusterPolicyReport',
                metadata: [name: 'partial'],
                summary: [pass: 3, error: 2], // missing other keys
                results: []
        ])

        when:
        r1.merge(r2)

        then:
        r1.summary == [pass: 3, fail: 0, warn: 0, error: 2, skip: 0]
    }

    def "merge with null results does not throw"() {
        given:
        def r1 = new PolicyReport()
        def r2 = new PolicyReport([summary: [pass: 1]])

        r2.results = null

        when:
        r1.merge(r2)

        then:
        noExceptionThrown()
        r1.summary.pass == 1
        r1.results == []
    }

    def "toMap returns correct structure"() {
        given:
        def report = new PolicyReport([
                apiVersion: 'a',
                kind: 'k',
                metadata: [name: 'r'],
                summary: [pass: 1],
                results: [[policy: 'x']]
        ])

        when:
        def map = report.toMap()

        then:
        map == [
                apiVersion: 'a',
                kind: 'k',
                metadata: [name: 'r'],
                summary: [pass: 1],
                results: [[policy: 'x']]
        ]
    }
}