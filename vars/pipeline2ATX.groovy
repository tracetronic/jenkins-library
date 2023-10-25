/*
 * Copyright (c) 2023 TraceTronic GmbH
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
 * Generates a TEST-GUIDE compatible JSON report of a pipeline build including logs and stage meta data.
 * The report is compressed as a zip file within the build workspace and can be uploaded using the JSON2ATX converter.
 *
 * This method can be called downstream or within a running build.
 * Without passing job and build parameters the current build is used as reference.
 * </br>
 * <b>Prerequisite:</b>
 * <ul>
 *<li><a href="https://plugins.jenkins.io/pipeline-utility-steps/">Pipeline Utility Steps Plugin</a></li>
 *<li><a href="https://plugins.jenkins.io/pipeline-stage-view/">Pipeline Stage View Plugin</a></li>
 *</ul>
 *
 * @param log
 *      <code>true</code>: each step log is passed to the test step description
 *      <code>false</code>: only the log file will be archived (optional and default)
 * @param jobName
 *      the name of the pipeline job (optional)
 * @param buildNumber
 *      the number of the pipeline build (optional)
 */
def call(log = false, jobName = '', int buildNumber = 0) {
    def build
    def logFile = ''

    build = getRawBuild(jobName, buildNumber)
    if (!build) {
        error "Job ${jobName} with build number ${buildNumber} not found!"
    }

    def filename = "${build.getParent().getDisplayName()}_${build.getNumber()}"
    def attributes = getBuildAttributes(build)
    def executionSteps = getExecutionSteps(build, log)

    if (log) {
        logText = getConsoleLog(build)
        logFile = "${filename}.log"
    }
    def json = generateJsonReport(build, attributes, executionSteps, logFile)
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
 * Collects all relevant build information and parameter as a map.
 *
 * @param build
 *      the pipeline raw build
 * @return the collected build information and parameters
 */
def getBuildAttributes(build) {
    def attributes = []
    def buildUrl = build.absoluteUrl
    def buildId = build.id
    def buildAttributes = [BUILD_URL: buildUrl, BUILD_ID: buildId]
    buildAttributes.putAll(params)
    buildAttributes.each { k, v ->
        if (v) {
            attributes.add([key: k, value: v.toString()])
        }
    }
    return attributes
}

/**
 * Generates a TEST-GUIDE compatible JSON report of the pipeline build.
 *
 * @param build
 *      the pipeline build
 * @param attributes
 *      the collected build attributes
 * @param executionTestSteps
 *      the stages of the pipeline build
 * @param logFile
 *      the log file name if per-step logging is enabled
 * @return the formatted JSON report
 */
def generateJsonReport(build, attributes, executionTestSteps, logFile) {
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
    testcase.put("executionTestSteps", executionTestSteps)
    def testCases = [testcase]

    JsonBuilder jsonBuilder = new JsonBuilder([name     : 'JenkinsPipeline',
                                               timestamp: build.getStartTimeInMillis(),
                                               testcases: testCases])
    String jsonString = jsonBuilder
    return JsonOutput.prettyPrint(jsonString)
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
 * Maps the given build result to according TEST-GUIDE verdict.
 *
 * @param result
 *      the build result
 * @return the according TEST-GUIDE verdict
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
        // trim arguments, as it could be a long script
        arguments = arguments.replaceAll("[\\n\\r\\t]", " ").replaceAll("\\s{2,}", " ")
        if (arguments.length() > (allowedSchemaMaxNameLength - name.length() - 3)) {
            arguments = arguments.take(allowedSchemaMaxNameLength - name.length() - 6) + "..."
        }
        name = name + " ("+arguments+")"
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
