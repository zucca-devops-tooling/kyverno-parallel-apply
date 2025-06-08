package dev.zucca_ops

class PolicyReport {

    String apiVersion
    String kind
    Map metadata
    Map summary
    List<Map> results

    PolicyReport() {
        this.apiVersion = 'wgpolicyk8s.io/v1alpha2'
        this.kind = 'ClusterPolicyReport'
        this.metadata = [name: "final-merged-report-${System.currentTimeMillis()}"]
        this.summary = [pass: 0, fail: 0, warn: 0, error: 0, skip: 0]
        this.results = []
    }

    PolicyReport(Map parsedYaml) {
        this.apiVersion = parsedYaml.apiVersion
        this.kind = parsedYaml.kind
        this.metadata = parsedYaml.metadata as Map
        this.summary = parsedYaml.summary ?: [:] as Map
        this.results = parsedYaml.results ?: [] as List<Map>
    }

    void merge(PolicyReport otherReport) {
        this.summary.pass += otherReport.summary.pass ?: 0
        this.summary.fail += otherReport.summary.fail ?: 0
        this.summary.warn += otherReport.summary.warn ?: 0
        this.summary.error += otherReport.summary.error ?: 0
        this.summary.skip += otherReport.summary.skip ?: 0

        this.results.addAll(otherReport.results)
    }

    /**
     * Converts the object into a Map. This map will be passed
     * directly to the writeYaml step.
     */
    Map toMap() {
        return [
                apiVersion: this.apiVersion,
                kind: this.kind,
                metadata: this.metadata,
                summary: this.summary,
                results: this.results
        ]
    }
}
