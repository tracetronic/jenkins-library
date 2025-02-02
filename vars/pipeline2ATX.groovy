/*
 * Copyright (c) 2023 - 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */

import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.workflow.flownode.FlowNodeUtil
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction
import org.jenkinsci.plugins.workflow.graph.AtomNode
import org.jenkinsci.plugins.workflow.graph.BlockStartNode
import org.jenkinsci.plugins.workflow.support.actions.LogStorageAction
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable

/**
 * Generates a test.guide compatible JSON report of a pipeline build including logs, metrics, and stage meta data.
 * The report is compressed as a zip file within the build workspace and can be uploaded using the JSON2ATX converter.
 *
 * This method can be called downstream or within a running build.
 * Without passing job and build parameters the current build is used as reference.
 * </br>
 * <b>Prerequisite:</b>
 * <ul>
 * <li><a href="https://plugins.jenkins.io/pipeline-utility-steps/">Pipeline Utility Steps Plugin</a></li>
 * <li><a href="https://plugins.jenkins.io/pipeline-stage-view/">Pipeline Stage View Plugin</a></li>
 * </ul>
 * <b>Additional test case information:</b>
 * <ul>
 * <li>Test case attribute "PRODUCT_NAME" with the name of the software product (only added, if the environment variable "PRODUCT_NAME" is specified).</li>
 * <li>Test case attribute "TEST_LEVEL" with the name of the corresponding test level (only added, if the environment variable "TEST_LEVEL" is specified).</li>
 * <li>Test case attribute "GIT_URL" with the url of the git repository (automatically added by using the Jenkins Plugin "Git", or explicitly added if the environment variable "GIT_URL" is specified).</li>
 * <li>Test case attribute "JENKINS_PIPELINE" with the name of the current jenkins pipeline.</li>
 * <li>Test case attribute "JENKINS_URL" with the url to the current jenkins pipeline job.</li>
 * <li>Test case attribute "JENKINS_WORKSPACE" with the path to the current jenkins workspace.</li>
 * <li>Test case constant "PRODUCT_VERSION" with the version string of the product (only added, if the environment variable "PRODUCT_VERSION" is specified).</li>
 * <li>Test case constant "GIT_COMMIT" with the version string of the product (automatically added by using the Jenkins Plugin "Git", or explicitly added if the environment variable "GIT_COMMIT" is specified).</li>
 * <li>Test case constant "JENKINS_BUILD_ID" with the build number of the current jenkins pipeline.</li>
 * <li>Test case constant "JENKINS_EXECUTOR_NUMBER" with the number that identifies the jenkins executor.</li>
 * <li>Test case constant "JENKINS_NODE_NAME" with the name of the node the current build is running on.</li>
 * </ul>
 * @param log
 *      <code>true</code>: each step log is passed to the test step description
 *      <code>false</code>: only the log file will be archived (optional and default)
 * @param jobName
 *      the name of the pipeline job (optional)
 * @param buildNumber
 *      the number of the pipeline build (optional)
 * @param customAttributes
 *      the custom attributes for the pipline build (optional)
 * @param customConstants
 *      the custom constants for the pipeline build (optional)
 */
def call(log = false, jobName = '', int buildNumber = 0, def customAttributes = [:], def customConstants = [:]) {
    def build
    def logFile = ''

    build = getRawBuild(jobName, buildNumber)
    if (!build) {
        error "Job ${jobName} with build number ${buildNumber} not found!"
    }

    def filename = "${build.getParent().getName()}_${build.getNumber()}"
    def attributes = getBuildAttributes(build, customAttributes)
    def constants = getBuildConstants(build, customConstants)
    def executionSteps = getExecutionSteps(build, log)
    def metrics = getBuildMetrics(build, executionSteps)

    if (log) {
        logText = getConsoleLog(build)
        logFile = "${filename}.log"
    }
    def json = generateJsonReport(build, attributes, constants, executionSteps, metrics, logFile)
    // reset build because it's not serializable
    build = null

    if (logFile) {
        writeFile file: logFile, text: logText
    }
    writeJSON file: "${filename}.json", json: json
    zip glob: "${filename}.log, ${filename}.json", zipFile: "${filename}_atx.zip"
}

/**
 * Gets the pipeline raw build by given job name and build number,.
 * If not specified the current build is used as reference.
 *
 * @param jobName
 *      the name of the pipeline job
 * @return the number of the pipeline build
 */
def getRawBuild(String jobName, int buildNumber) {
    if (jobName && buildNumber != 0) {
        def item = Jenkins.get().getItemByFullName(jobName)
        return item?.getBuildByNumber(buildNumber)
    } else {
        return currentBuild.rawBuild
    }
}

/**
 * Collects all relevant build information and parameters as a key-value map:
 * <ul>
 *   <li><code>PRODUCT_NAME</code>: Content of the env var PRODUCT_NAME, only added if present.</li>
 *   <li><code>GIT_URL</code>: Content of env var GIT_URL, only added if present.</li>
 *   <li><code>JENKINS_PIPELINE</code>: Name of the current Jenkins build job.</li>
 *   <li><code>JENKINS_URL</code>: URL to the current Jenkins build job.</li>
 *   <li><code>JENKINS_WORKSPACE</code>: Path to the Jenkins pipeline workspace.</li>
 *   <li><code>TEST_LEVEL</code>: Content of env var TEST_LEVEL, only added if present.</li>
 * </ul>
 *
 *  customAttributes overrides build attributes (when adding maps the second map will override values present in both)
 * @param build
 *      the pipeline raw build
 * @return the collected build information and parameters in ATX attribute format
 */
def getBuildAttributes(build, customAttributes) {
    def attributes = [PRODUCT_NAME: env.PRODUCT_NAME,
                          GIT_URL: env.GIT_URL,
                          JENKINS_PIPELINE: build.getDisplayName(),
                          JENKINS_URL: build.getAbsoluteUrl(),
                          JENKINS_WORKSPACE: env.WORKSPACE,
                          TEST_LEVEL: env.TEST_LEVEL] + customAttributes
    return attributes.findAll{k,v -> v}.collect{ k, v -> 
        if (v instanceof List) {
            return [key: k, value: v.collect{it.toString()}]
        }
        return [key: k, value: v.toString()]
    }
}

/**
 * Collects all relevant build information and parameters as a key-value map:
 * <ul>
 *   <li><code>PRODUCT_VERSION</code>: Content of env var PRODUCT_VERSION, only added if present.</li>
 *   <li><code>GIT_COMMIT</code>: Content of env var GIT_COMMIT, only added if present.</li>
 *   <li><code>JENKINS_BUILD_ID</code>: Number of current Jenkins build.</li>
 *   <li><code>JENKINS_EXECUTOR_NUMBER</code>: Number of current Jenkins executor.</li>
 *   <li><code>JENKINS_NODE_NAME</code>: Name of current node the current build is running on.</li>
 * </ul>
 *
 * customConstants overrides build constants (when adding maps the second map will override values present in both)
 * @param build
 *      the pipeline raw build
 * @return the collected build information and parameters in ATX constants format
 */
def getBuildConstants(build, customConstants) {
    def constants = [PRODUCT_VERSION: env.PRODUCT_VERSION,
                         GIT_COMMIT: env.GIT_COMMIT,
                         JENKINS_BUILD_ID: build.id,
                         JENKINS_EXECUTOR_NUMBER: env.EXECUTOR_NUMBER,
                         JENKINS_NODE_NAME: env.NODE_NAME] + customConstants
    return constants.findAll{k,v -> v}.collect{ k, v -> 
        if (v instanceof List) {
            return [key: k, value: v.collect{it.toString()}]
        }
        return [key: k, value: v.toString()]
    }
}

/**
 * Collects all relevant build metrics information and parameters as a key-value map:
 * <ul>
 *   <li><code>TOTAL_EXECUTION_TIME</code>: Full run time of the build in seconds.</li>
 *   <li><code>SETUP_TIME</code>: Full run time of the setup phase of the build in seconds.</li>
 *   <li><code>EXECUTION_TIME</code>: Full run time of the execution phase of the build in seconds.</li>
 *   <li><code>TEARDOWN_TIME</code>: Full run time of the teardown phase of the build in seconds.</li>
 *   <li><code>SETUP_PERCENTAGE</code>: Percentage of the setup phase in relation to the TOTAL_EXECUTION_TIME.</li>
 *   <li><code>EXECUTION_PERCENTAGE</code>: Percentage of the execution phase in relation to the TOTAL_EXECUTION_TIME.</li>
 *   <li><code>TEARDOWN_PERCENTAGE</code>: Percentage of the teardown phase in relation to the TOTAL_EXECUTION_TIME.</li>
 *   <li><code>QUEUE_TIME</code>: Time until the build starts in seconds.</li>
 *   <li><code>COMMIT_TO_START_TIME</code>: Time from commit to start of the build in seconds, only for push-based executions.</li>
 *   <li><code>TIME_TO_ERROR</code>: Time until an error occurred in seconds, only for stages in the execution phase.</li>
 * </ul>
 *
 * Values that not always occur, such as <code>TIME_TO_ERROR</code>, will be filtered out before collection
 * @param build
 *      the pipeline raw build
 * @param testExecutionSteps
 *      the stages of the pipeline build
 * @return the collected build information and parameters in ATX parameters format
 */
def getBuildMetrics(build, testExecutionSteps) {
    def timeValues = calculateTime(testExecutionSteps, build)
    def metrics = [
            [name: "TOTAL_EXECUTION_TIME", direction: "OUT", value: timeValues.totalDuration],
            [name: "SETUP_TIME", direction: "OUT", value: timeValues.setupDuration],
            [name: "EXECUTION_TIME", direction: "OUT", value: timeValues.executionDuration],
            [name: "TEARDOWN_TIME", direction: "OUT", value: timeValues.teardownDuration],
            [name: "SETUP_PERCENTAGE", direction: "OUT", value: timeValues.setupPercentage],
            [name: "EXECUTION_PERCENTAGE", direction: "OUT", value: timeValues.executionPercentage],
            [name: "TEARDOWN_PERCENTAGE", direction: "OUT", value: timeValues.teardownPercentage],
            [name: "QUEUE_TIME", direction: "OUT", value: timeValues.queueDuration],
            [name: "COMMIT_TO_START_TIME", direction: "OUT", value: timeValues.fromCommitToStartTime],
            [name: "TIME_TO_ERROR", direction: "OUT", value: timeValues.errorTime]
    ]
    return metrics.findAll { param -> param.value != null }
}

/**
 * Generates a test.guide compatible JSON report of the pipeline build.
 *
 * @param build
 *      the pipeline build
 * @param attributes
 *      the collected attributes
 * @param constants
 *      the collected constants
 * @param executionTestSteps
 *      the stages of the pipeline build
 * @param parameters
 *      the collected parameters
 * @param logFile
 *      the log file name if per-step logging is enabled
 * @return the formatted JSON report
 */
def generateJsonReport(build, attributes, constants, executionTestSteps, parameters, logFile) {
    Map testcase = [:]

    testcase.put("@type", "testcase")
    testcase.put("name", build.getParent().getDisplayName())
    testcase.put("verdict", resultToATXVerdict(getCurrentResult(build)))
    testcase.put("description", build.getParent().getDescription())
    testcase.put("timestamp", build.getStartTimeInMillis())
    testcase.put("executionTime", (int) ((build.getStartTimeInMillis() - build.getTimeInMillis()) / 1000))
    testcase.put("attributes", attributes)
    if (logFile) {
        testcase.put("artifacts", [logFile])
    }
    testcase.put("constants", constants)
    testcase.put("executionTestSteps", executionTestSteps)
    testcase.put("parameters", parameters)
    def testCases = [testcase]

    JsonBuilder jsonBuilder = new JsonBuilder([name     : 'JenkinsPipeline',
                                               timestamp: build.getStartTimeInMillis(),
                                               testcases: testCases])
    String jsonString = jsonBuilder
    return JsonOutput.prettyPrint(jsonString)
}

/**
 *  Calculates the duration metrics and their percentages.
 *
 * @param executionTestSteps
 *      the stages of the pipeline build
 * @param build
 *      the pipeline build
 * @return a map containing the calculated durations in seconds and percentages
 */
def calculateTime(executionTestSteps, build) {
    def setupDuration = 0.0
    def executionDuration = 0.0
    def teardownDuration = 0.0
    def queueDuration = (build.getStartTimeInMillis() - build.getTimeInMillis()) / 1000.0
    def errorTime = calculateErrorTime(executionTestSteps)
    def fromCommitToStartTime = currentBuild.getBuildCauses('jenkins.branch.BranchEventCause').isEmpty() ? null : getTimeFromCommitToStart(build)

    executionTestSteps.each { stage ->
        // Update the current phase based on the stage name
        def stagePhase = getStagePhase(stage.name)

        if (stagePhase == 'teardown') {
            teardownDuration += stage.duration
        } else if (stagePhase == 'setup') {
            setupDuration += stage.duration
        } else if (stagePhase == 'execution') {
            executionDuration += stage.duration
        }
    }

    def totalDuration = queueDuration + setupDuration + executionDuration + teardownDuration

    def setupPercentage = 0.0
    def executionPercentage = 0.0
    def teardownPercentage = 0.0

    if (totalDuration > 0) {
        setupPercentage = (setupDuration / totalDuration) * 100
        executionPercentage = (executionDuration / totalDuration) * 100
        teardownPercentage = (teardownDuration / totalDuration) * 100
    }

    return [
            setupDuration: convertTimeValueToDouble(setupDuration),
            setupPercentage: convertTimeValueToDouble(setupPercentage),
            executionDuration: convertTimeValueToDouble(executionDuration),
            executionPercentage: convertTimeValueToDouble(executionPercentage),
            teardownDuration: convertTimeValueToDouble(teardownDuration),
            teardownPercentage: convertTimeValueToDouble(teardownPercentage),
            queueDuration: convertTimeValueToDouble(queueDuration),
            totalDuration: convertTimeValueToDouble(totalDuration),
            fromCommitToStartTime: fromCommitToStartTime != null ? convertTimeValueToDouble(fromCommitToStartTime) : null,
            errorTime: errorTime != null ? convertTimeValueToDouble(setupDuration + queueDuration + errorTime) : null]
}

/**
 * Converts a none null time value to a double with one decimal place.
 * @param value
 *      the time value to be converted
 * @return the time value converted as double
 */
def convertTimeValueToDouble(def value) {
    return String.format("%.1f", value).replace(',', '.').toDouble()
}

/**
 * Determines the time until an error within the execution phase occurs.
 *
 * @param executionTestSteps
 *      the stages of the pipeline build
 * @return the duration value in seconds or null in case of a error free build
 */
def calculateErrorTime(executionTestSteps) {
    def errorTime = null
    def accumulatedTime = 0

    executionTestSteps.each { stage ->
        if (stage == null) {
            return
        }

        def stageDuration = stage.get('duration', 0)
        def teststeps = stage.get('teststeps', [])
        def stageErrorTime = findNonPassedVerdict(teststeps, stageDuration)
        if (stageErrorTime != null) {
            errorTime = accumulatedTime + stageErrorTime
            return errorTime
        }
        accumulatedTime += stageDuration
    }
    return errorTime
}

/**
 * Determines the respective phase of the stage being examined.
 *
 * @param stageName
 *      the name of the stage
 * @return the current phase
 */
def getStagePhase(stageName) {
    if (stageName.contains("Post Actions") || stageName.contains("Post:")) {
        return 'teardown'
    } else if (stageName.contains("Declarative:") || stageName.contains("Pre:")) {
        return 'setup'
    }
    return 'execution'
}

/**
 * Scans the verdicts value while traversing the stages of the pipeline build
 *
 * @param steps
 *      the steps within a stage
 * @param accumulatedTime
 *      accumulated duration values until an error occurred
 * @return the duration value in seconds or null in case of a error free build
 */
def findNonPassedVerdict(steps, accumulatedTime) {
    def errorTime = null
    steps.each { step ->
        if (step == null) {
            return
        }

        def stepType = step.get('@type')
        if (stepType == 'teststep') {
            def verdict = step.get('verdict')
            if (verdict == 'FAILED' || verdict == 'ERROR') {
                errorTime = accumulatedTime
                return errorTime
            }
        } else if (stepType == 'teststepfolder') {
            def nestedSteps = step.get('teststeps', [])
            errorTime = findNonPassedVerdict(nestedSteps, accumulatedTime)
            if (errorTime != null) {
                return errorTime
            }
        }
    }
    return errorTime
}

/**
 * Determines the time from commit to start of the build
 *
 * @param build
 *      the pipeline build
 * @return the duration value in seconds
 */
@NonCPS
def getTimeFromCommitToStart(build) {
    def commitTime = null
    def startTime = build.getStartTimeInMillis() / 1000
    try {
        def process = ["git", "-C", env.WORKSPACE, "show", "-s", "--format=%ct", env.GIT_COMMIT].execute()
        def output = new StringBuffer()
        def error = new StringBuffer()
        process.consumeProcessOutput(output, error)
        process.waitFor()

        if (process.exitValue() == 0) {
            def commitTimeStamp = output.toString().trim().toLong()
            commitTime = startTime - commitTimeStamp
        }
    } catch (Exception e) {
        println("Exception occurred: ${e.message}")
    }
    return commitTime
}

/**
 * Gets the current build result.
 *
 * @param build
 *      the pipeline build
 * @return the current result of the build as String
 */
def getCurrentResult(build) {
    if (build.isInProgress()) {
        def wrapper = new RunWrapper(build, true)
        return wrapper.getCurrentResult()
    } else {
        return build.getResult().toString()
    }
}

/**
 * Gets the console log.
 *
 * @param build
 *      the pipeline build
 * @return the console log of the build
 */
def getConsoleLog(build) {
    def log = ''
    def logs = build.getLog(Integer.MAX_VALUE)
    if (logs) {
        log = logs.join(System.lineSeparator())
    }
    return log
}

/**
 * Maps the given build result to according test.guide verdict.
 *
 * @param result
 *      the build result
 * @return the according test.guide verdict
 */
@NonCPS
static def resultToATXVerdict(result) {
    def verdict
    switch (result) {
        case 'SUCCESS':
            verdict = 'PASSED'
            break
        case 'UNSTABLE':
            verdict = 'FAILED'
            break
        case ['FAILURE', 'FAILED']:
            verdict = 'ERROR'
            break
        default:
            verdict = 'NONE'
    }
    return verdict
}

/**
 * Create test execution steps from the current pipeline build.
 *
 * @param build
 *      the pipeline build
 * @param appendLogs
 *      if true, the individual logs of test steps are added in their description field
 * @return the test execution steps of the pipeline build
 */
@NonCPS
def getExecutionSteps(build, appendLogs) {
    def executionTestSteps = []

    // Table might not exist before execution is finished
    FlowGraphTable table = new FlowGraphTable(build.getExecution())
    table.build()

    // Get start of relevant pipeline steps
    def Stages = table.getRows().stream().findAll { r -> r.getDisplayName() == "stage" }.toArray()
    for (FlowGraphTable.Row row : Stages) {
        def item = crawlRows(row, appendLogs, false)
        if (item) {
            executionTestSteps.add(item)
        }
    }
    return executionTestSteps
}

/**
 * Crawls the current table row for corresponding test execution step items.
 * Every row checks its child elements and its sibling recursively.
 * Only description, test step and test step folder items are considered.
 *
 * @param row
 *      the current table row
 * @param appendLogs
 *      if true, the individual logs of test steps are added in their description field
 * @param insideStage
 *      flag to indicate that crawling is done inside a stage block
 * @return the test execution steps of the current table row
 */
@NonCPS
def crawlRows(row, appendLogs, insideStage=false) {
    def node = row.getNode()

    if (insideStage && row.getDisplayName() == "stage") {
        // all stages are handled on top-level -> when a stage inside a stage is found stop going down the hierarchy
        def item = createTestStep(row, false)
        item["name"] = "creating " + item["name"]
        return item
    } else if (node instanceof AtomNode) {
        // AtomNodes = test steps
        return createTestStep(row, appendLogs)
    } else if (node instanceof BlockStartNode) {
        // blocks = test step folders
        return createTestStepFolder(row, appendLogs)
    }
    // Pipeline element cannot be mapped to an ATX item -> skip row
    def child = row.firstTreeChild
    if (child) {
        return crawlRows(child, appendLogs, insideStage)
    }
    return [:]
}

/**
 * Create a description from the current pipeline step.
 *
 * @param row
 *      the current table row
 * @return the description as a string (with a max length of 255)
 */
@NonCPS
def getDescription(row) {
    def allowedSchemaMaxStringLength = 120

    def logText = getLogText(row.getNode())

    if (logText.length() > allowedSchemaMaxStringLength) {
        // Set allowedSchemaMaxStringLength to 117 to be able to concatenate it with "..."
        return logText.take(allowedSchemaMaxStringLength - 3) + "..."
    }
    return logText
}

/**
 * Creates the test step name by combining the row name with its arguments
 * @param row
 *      the current table row
 * @return the test step name as a string (with a max length of 255)
 */
@NonCPS
def getTestStepName(row) {
    def allowedSchemaMaxNameLength = 120
    String name = row.getDisplayName()

    def arguments = ArgumentsAction.getStepArgumentsAsString(row.getNode())
    if (arguments) {
        arguments = arguments.replaceAll(/\s+/, ' ').trim()

        def maxArgumentLength = allowedSchemaMaxNameLength - name.length() - 6
        arguments = arguments.length() > maxArgumentLength ? arguments.take(maxArgumentLength) + "..." : arguments

        name = "$name ($arguments)"
    }

    return name
}

/**
 * Create a test step item from the current pipeline step.
 *
 * @param row
 *      the current table row
 * @param appendLogs
 *      if true, the logs of row/node are added in the description field
 * @param skipped
 *      possibility to mark the test step as skipped (usually for block nodes)
 * @return the test step as a map
 */
@NonCPS
def createTestStep(row, appendLogs, skipped = false) {
    def node = row.getNode()
    Map testStep = [:]
    def name = getTestStepName(row)
    def status = FlowNodeUtil.getStatus(node).toString()
    def verdict = resultToATXVerdict(status)

    if (skipped || node.getAction(NotExecutedNodeAction.class)) {
        name = name + " --> skipped"
        verdict = "NONE"
    }

    testStep.put("@type", "teststep")
    testStep.put("name", name)
    testStep.put("verdict", verdict)

    if (appendLogs) {
        def description = getDescription(row)
        if (description) {
            testStep.put("description", description)
        }
    }
    return testStep
}

/**
 * Create a test step folder item from the current pipeline step.
 *
 * @param row
 *      the current table row
 * @param appendLogs
 *      if true, the logs of row/node are added in the description field
 * @return the test step folder as a map
 */
@NonCPS
def createTestStepFolder(row, appendLogs) {
    def duration = row.getDurationMillis() / 1000
    Map testStepFolder = [:]
    def name = getTestStepName(row)

    def child = row.firstTreeChild
    def skipped = false
    // there are always two BlockStartNodes stacked. The outer is the actual block, the inner one encapsulates the
    // body (identified by a BodyInvocationAction). Most of the time a block has just one body, so it does not have
    // any additional value and can be ignored
    // One example case for having multiple bodies is a "parallel" block
    if (child && child.getNode().getAction(BodyInvocationAction.class) && !(child.nextTreeSibling)) {
        // check if the block was not executed
        if (child.getNode().getAction(NotExecutedNodeAction.class)) {
            skipped = true
        }
        child = child.firstTreeChild
    }
    def testSteps = []
    while (child) {
        def testStepItem = crawlRows(child, appendLogs, true)
        if (testStepItem) {
            testSteps.add(testStepItem)
        }
        child = child.nextTreeSibling
    }

    // if no inner test steps where found, convert row into a test step instead
    if (!testSteps || skipped) {
        return createTestStep(row, appendLogs, skipped)
    }

    testStepFolder.put("@type", "teststepfolder")
    testStepFolder.put("name", name)
    testStepFolder.put("duration", duration)
    testStepFolder.put("teststeps", testSteps)

    if (appendLogs) {
        def description = getDescription(row)
        if (description) {
            testStepFolder.put("description", description)
        }
    }
    return testStepFolder
}

/**
 * Get the log of a node.
 *
 * @param node
 *      the current node
 * @return the log of the node
 */
@NonCPS
def getLogText(node) {
    def log = ''
    def logAction = node.getAction(LogStorageAction.class)
    if (!logAction) {
        return log
    }

    def annotated = logAction.getLogText()
    if (!annotated) {
        return log
    }

    def logLen = annotated.length()
    if (logLen > 0) {
        def writer = new StringWriter()
        try {
            annotated.writeHtmlTo(0, writer)
            log = writer.toString()
        } catch (e) {
            println "Error serializing log for ${e}"
        }
    }
    return log
}
