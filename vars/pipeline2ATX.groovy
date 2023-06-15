/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.workflow.flownode.FlowNodeUtil
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.actions.LabelAction
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
    String logFile

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
        writeFile file: logFile, text: logText
    }

    def json = generateJsonReport(build, attributes, executionSteps, logFile)
    // reset build because it's not serializable
    build = null

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
        attributes.add([key: k, value: v.toString()])
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
 * @param debug
 *      the log level
 * @return the test execution steps of the pipeline build
 */
@NonCPS
def getExecutionSteps(build, debug) {
    def executionTestSteps = []

    // Table might not exist before execution is finished
    FlowGraphTable table = new FlowGraphTable(build.getExecution())
    table.build()

    // Get start of relevant pipeline steps
    def topLevelRows = table.getRows().stream().findAll { r -> r.getTreeDepth() == 3 }.toArray()
    for (FlowGraphTable.Row row : topLevelRows) {
        def item = crawlRows(row, debug)
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
 * @param debug
 *      the log level
 * @return the test execution steps of the current table row
 */
@NonCPS
def crawlRows(row, debug) {
    def atxItem = [:]
    def node = row.getNode()
    def item

    if (isDescription(row) && debug) {
        item = createDescription(row)
    } else if (isTestStep(row) && !node.isActive()) {
        // Search inside a active stage
        item = createTestStep(row, debug)
    } else if (isTestStepFolder(row)) {
        // Stages might have multiple stages, ignore the active stage
        item = createTestStepFolder(row, debug)
    } else {
        // Pipeline element cannot be mapped to an ATX item, search children and siblings instead
        def child = row.firstTreeChild
        while (child) {
            item = crawlRows(child, debug)
            child = child.nextTreeSibling
        }
    }

    if (item) {
        if (item instanceof Map) {
            atxItem.putAll(item)
        } else {
            return item
        }
    }
    return atxItem
}

/**
 * Determines whether the current pipeline step corresponds to a description item.
 *
 * @param row
 *      the current table row
 * @return {@code true} if description, {@code false} otherwise
 */
@NonCPS
def isDescription(row) {
    def node = row.getNode()
    return (node.getAction(LogStorageAction.class) &&
            node instanceof AtomNode && !row.firstTreeChild)
}

/**
 * Determines whether the current pipeline step corresponds to a test step item.
 *
 * @param row
 *      the current table row
 * @return {@code true} if test step, {@code false} otherwise
 */
@NonCPS
def isTestStep(row) {
    def node = row.getNode()

    if (!(node.getAction(LabelAction.class) && node instanceof BlockStartNode)) {
        return false
    }

    // Stage is parent of step or empty
    if (!row.firstTreeChild) {
        return true
    }

    // Determine node descriptor and verify a script step
    String scriptDescriptor = row.firstTreeChild.getNode().descriptorId
    def isScript = scriptDescriptor.endsWith('ScriptStep')
    if (isDescription(row.firstTreeChild) || isScript) {
        return true
    }

    return false
}

/**
 * Determines whether the current pipeline step corresponds to a test folder item.
 *
 * @param row
 *      the current table row
 * @return {@code true} if test step folder, {@code false} otherwise
 */
@NonCPS
def isTestStepFolder(row) {
    def node = row.getNode()
    def child = row.firstTreeChild
    if (!node.getAction(LogStorageAction.class) && node instanceof BlockStartNode) {
        return (child && !isDescription(child))
    } else {
        return false
    }
}

/**
 * Create a description from the current pipeline step.
 *
 * @param row
 *      the current table row
 * @return the description as an array
 */
@NonCPS
def createDescription(row) {
    def node = row.getNode()
    def description = []
    def logText = getLogText(node)

    if (logText) {
        description.add(logText)
    }
    return description
}

/**
 * Create a test step item from the current pipeline step.
 *
 * @param row
 *      the current table row
 * @return the test step as a map
 */
@NonCPS
def createTestStep(row, debug) {
    def node = row.getNode()
    Map testStep = [:]
    def name = node.getDisplayName()
    def status = FlowNodeUtil.getStatus(node).toString()
    def verdict = resultToATXVerdict(status)

    testStep.put("@type", "teststep")
    testStep.put("name", name)
    testStep.put("verdict", verdict)

    def child = row.firstTreeChild
    def description = []
    while (debug && child) {
        // Stage might has multiple steps
        description.addAll(crawlRows(child, true))
        child = child.nextTreeSibling
    }
    def allowedSchemaMaxStringLength = 117
    testStep.put("description", description.join("").take(allowedSchemaMaxStringLength) + '...')
    return testStep
}

/**
 * Create a test step folder item from the current pipeline step.
 *
 * @param row
 *      the current table row
 * @return the test step folder as a map
 */
@NonCPS
def createTestStepFolder(row, debug) {
    def node = row.getNode()
    Map testStepFolder = [:]
    def name = node.getDisplayName()

    def child = row.firstTreeChild
    def testSteps = []
    while (child) {
        // Stages might have more than one stage
        if (crawlRows(child, debug)) {
            testSteps.add(crawlRows(child, debug))
        }
        child = child.nextTreeSibling
    }

    // Safety downstream handling
    if (!testSteps) {
        return testStepFolder
    }

    testStepFolder.put("@type", "teststepfolder")
    testStepFolder.put("name", name)
    testStepFolder.put("teststeps", testSteps)
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

    def logLen = annotated.length();
    if (logLen > 0) {
        def writer = new StringWriter();
        try {
            annotated.writeHtmlTo(0, writer);
            log = writer.toString();
        } catch (e) {
            println "Error serializing log for ${e}";
        }
    }
    return log
}
