package dev.zucca_ops

import spock.lang.Specification

/**
 * Unit tests for the WorkspaceManager class.
 * This spec verifies path construction and directory management command generation.
 */
class WorkspaceManagerSpec extends Specification {

	// A mock 'steps' object to intercept calls to Jenkins pipeline steps like 'sh' and 'echo'.
	def steps

	// A standard WorkspaceManager instance to be used in tests.
	def workspace

	def setup() {
		// We initialize a new mock and a new WorkspaceManager instance for each test
		// to ensure the tests are isolated from each other.
		steps = new Expando(echo: { }) // Default echo mock

		workspace = new WorkspaceManager('/var/lib/jenkins/workspace/my-job', '42')
	}

	def "should construct correct absolute paths for all managed directories"() {
		expect: "all path getters return the correct, fully-qualified paths"
		workspace.getWorkspaceRoot() == '/var/lib/jenkins/workspace/my-job/.workspace/run-42'
		workspace.getShardsBaseDirectory() == '/var/lib/jenkins/workspace/my-job/.workspace/run-42/shards'
		workspace.getShardDirectory(3) == '/var/lib/jenkins/workspace/my-job/.workspace/run-42/shards/3'
		workspace.getResultDirectory() == '/var/lib/jenkins/workspace/my-job/.workspace/run-42/results'
	}

	def "should correctly handle relative and absolute paths for helper methods"() {
		when: "getting a folder path"
		def relativeFolder = workspace.getFolder('my/relative/path')
		def absoluteFolder = workspace.getFolder('/an/absolute/path')

		and: "getting a shard log file path"
		def relativeLog = workspace.getShardLogFile('debug-logs', 5)
		def absoluteLog = workspace.getShardLogFile('/var/log/jenkins', 5)

		and: "getting a relative path from an absolute one"
		def calculatedRelative = workspace.getRelativePath('/var/lib/jenkins/workspace/my-job/some/artifact.txt')

		then: "the paths are constructed correctly"
		relativeFolder == '/var/lib/jenkins/workspace/my-job/my/relative/path'
		absoluteFolder == '/an/absolute/path'
		relativeLog == '/var/lib/jenkins/workspace/my-job/debug-logs/shard-5-debug.log'
		absoluteLog == '/var/log/jenkins/shard-5-debug.log'
		calculatedRelative == 'some/artifact.txt'
	}

	def "createDirectories should call 'sh' with correct mkdir commands"() {
		given: "a list to capture all calls to the 'sh' command"
		def shellCommands = []
		steps.sh = { String cmd -> shellCommands.add(cmd) }

		when: "we ask the workspace to create directories for 4 shards"
		workspace.createDirectories(steps, 4)

		then: "the correct number of mkdir commands should be executed"
		shellCommands.size() == 6 // 1 for base, 4 for shards, 1 for results

		and: "the commands should have the correct paths"
		shellCommands.contains("mkdir -p /var/lib/jenkins/workspace/my-job/.workspace/run-42/shards")
		shellCommands.contains("mkdir -p /var/lib/jenkins/workspace/my-job/.workspace/run-42/shards/0")
		shellCommands.contains("mkdir -p /var/lib/jenkins/workspace/my-job/.workspace/run-42/shards/1")
		shellCommands.contains("mkdir -p /var/lib/jenkins/workspace/my-job/.workspace/run-42/shards/2")
		shellCommands.contains("mkdir -p /var/lib/jenkins/workspace/my-job/.workspace/run-42/shards/3")
		shellCommands.contains("mkdir -p /var/lib/jenkins/workspace/my-job/.workspace/run-42/results")
	}

	def "cleanup should call 'sh' with the correct rm command"() {
		given: "a variable to capture the rm command"
		String executedCommand = ""
		steps.sh = { String cmd -> executedCommand = cmd }

		when: "we call the cleanup method"
		workspace.cleanup(steps)

		then: "the command should be a safe recursive delete of the temporary workspace root"
		executedCommand == "rm -rf /var/lib/jenkins/workspace/my-job/.workspace/run-42"
	}
}
