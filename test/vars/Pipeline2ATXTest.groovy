/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
import com.cloudbees.workflow.flownode.FlowNodeUtil
import groovy.testSupport.PipelineSpockTestBase
import hudson.console.AnnotatedLargeText
import org.jenkinsci.plugins.workflow.actions.LabelAction
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.graph.AtomNode
import org.jenkinsci.plugins.workflow.graph.BlockStartNode
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.support.actions.LogStorageAction
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable

class Pipeline2ATXTest extends PipelineSpockTestBase {

    def pipeline2ATX
    def scriptName = 'vars/pipeline2ATX.groovy'

    def setup() {
        pipeline2ATX = loadScript(scriptName)
    }

    def 'Collect build attributes and parameters'() {
        given: 'a build'
            def build = binding.getVariable('currentBuild')
            def buildProps = getCurrentBuild()
            build['id'] = buildProps.get('id')
            build['absoluteUrl'] = buildProps.get('url')
            addParam(result.last().get('key'), result.last().get('value'))

        when: 'collect the build attributes'
            List attributes = pipeline2ATX.getBuildAttributes(build)

        then: 'expect a attributes list with build information'
            result == attributes

        where:
            result = getAttributes()
    }

    def 'Get current build result'() {
        given: 'a build'
            def currentBuild = binding.getVariable('currentBuild')
            currentBuild['getResult'] = { return 'FAILED' }
            currentBuild['isInProgress'] = { return inProgress }

        when: 'get current build result'
            def result = pipeline2ATX.getCurrentResult(currentBuild)

        then: 'expect the current build result depends on progress status'
            result == 'FAILED'

        where:
            // ToDo: implement the rainy day
            inProgress = false
    }

    def 'Get console log'() {
        given: 'a build with log'
            def build = binding.getVariable('currentBuild')
            build['getLog'] = { return logText }

        when: 'get the console log'
            String consoleLog = pipeline2ATX.getConsoleLog(build)

        then: 'expect a log text with line separator'
            logText.join(System.lineSeparator()) == consoleLog

        where:
            logText << [['My first log text line'],
                        ['My first log text line', 'My second log text line'],
                        []]
    }

    def 'Map result to TEST-GUIDE verdict'() {
        expect: 'expect result is mapped to according TEST-GUIDE verdict'
            verdict == pipeline2ATX.resultToATXVerdict(result)

        where:
            result     | verdict
            'SUCCESS'  | 'PASSED'
            'UNSTABLE' | 'FAILED'
            'FAILED'   | 'ERROR'
            'FAILURE'  | 'ERROR'
            'INVALID'  | 'NONE'
    }

    def 'Test row corresponds to description'() {
        given: 'a description row with node'
            def node
            if (atomNode) {
                node = Mock(AtomNode)
            } else {
                node = Mock(BlockStartNode)
            }
            def row = new FlowGraphTable.Row(node)
            node.getAction(_) >> logStorage
            row.getNode() >> node
            if (child) {
                row.firstTreeChild = row
            } else {
                row.firstTreeChild = null
            }

        when: 'check row is description'
            Boolean description = pipeline2ATX.isDescription(row)

        then: 'expect is a description'
            description == result

        where:
            atomNode | logStorage             | child | result
            true     | Mock(LogStorageAction) | false | true
            false    | Mock(LogStorageAction) | false | false
            true     | null                   | false | false
            true     | Mock(LogStorageAction) | true  | false
    }

    def 'Test row corresponds to test step'() {
        given: 'a testStep row with node'
            def stepDescriptor = Mock(StepDescriptor)
            stepDescriptor.getId() >> script
            def stepStart = new StepStartNode(Mock(CpsFlowExecution),
                    stepDescriptor, Mock(FlowNode))
            def node
            if (startNode) {
                node = Mock(StepStartNode)
            } else {
                node = Mock(AtomNode)
            }
            def row = new FlowGraphTable.Row(node)
            def childRow = new FlowGraphTable.Row(stepStart)
            node.getAction(_) >> label
            row.getNode() >> node
            if (child) {
                row.firstTreeChild = childRow
            } else {
                row.firstTreeChild = null
            }
            helper.registerAllowedMethod('isDescription', [Object],
                    { return description })
            pipeline2ATX = loadScript(scriptName)

        when: 'check row is testStep'
            Boolean testStep = pipeline2ATX.isTestStep(row)

        then: 'expect is a testStep'
            testStep == result

        where:
            startNode | label                  | child | script         | description | result
            true      | Mock(LabelAction)      | true  | 'EchoStep'     | false       | false
            true      | Mock(LabelAction)      | true  | 'EchoStep'     | true        | true
            true      | Mock(LabelAction)      | true  | 'ScriptStep'   | false       | true
            true      | Mock(LabelAction)      | false | 'EchoStep'     | false       | true
            true      | Mock(LogStorageAction) | true  | 'EchoStep'     | false       | false
            false     | Mock(LabelAction)      | true  | 'EchoStep'     | false       | false
    }

    def 'Test row corresponds to test step folder'() {
        given: 'a testStepFolder row with node'
            def node
            if (startNode) {
                node = Mock(StepStartNode)
            } else {
                node = Mock(AtomNode)
            }
            def row = new FlowGraphTable.Row(node)
            node.getAction(_) >> logStorage
            row.getNode() >> node
            row.firstTreeChild = child

            helper.registerAllowedMethod('isDescription', [Object],
                    { return description })
            pipeline2ATX = loadScript(scriptName)

        when: 'check row is testStepFolder'
            Boolean testStepFolder = pipeline2ATX.isTestStepFolder(row)

        then: 'expect is a testStepFolder'
            result == testStepFolder

        where:
            startNode | logStorage             | child                    | description | result
            true      | null                   | Mock(FlowGraphTable.Row) | false       | true
            false     | null                   | Mock(FlowGraphTable.Row) | false       | false
            true      | Mock(LogStorageAction) | Mock(FlowGraphTable.Row) | false       | false
            true      | null                   | null                     | false       | false
            true      | null                   | Mock(FlowGraphTable.Row) | true        | false
    }

    def 'Create description item from row'() {
        given: 'a description row with node'
            def node = Mock(FlowNode)
            def row = Mock(FlowGraphTable.Row)
            row.getNode() >> node
            helper.registerAllowedMethod('getLogText', [Object], { return logText })
            pipeline2ATX = loadScript(scriptName)

        when: 'add description of node'
            List description = pipeline2ATX.createDescription(row)

        then: 'expect a list of strings or empty list'
            result == description

        where:
            logText << ['Test', null]
            result << [['Test'], []]
    }

    def 'Create test step item from row'() {
        // TODO: Add child and sibling test blocked by https://github.com/jenkinsci/JenkinsPipelineUnit/issues/337
        given: 'a row with node'
            def node = Mock(FlowNode)
            def row = new FlowGraphTable.Row(node)
            def childRow = new FlowGraphTable.Row(node)
            def siblingRow = new FlowGraphTable.Row(node)
            def testStepMap = getTestStep(false, false)
            def description = [getTestStep(false, false).get('description')]
            node.getDisplayName() >> testStepMap['name']

            FlowNodeUtil.metaClass.static.getStatus = { FlowNode n -> 'SUCCESS' }
            siblingRow.nextTreeSibling = null
            childRow.nextTreeSibling = null

            row.getNode() >> node
            row.firstTreeChild = null

            helper.registerAllowedMethod('resultToATXVerdict', [Object], { return testStepMap['verdict'] })
            // this won't work
            helper.registerAllowedMethod('crawlRows', [Object, Boolean], { return description })
            pipeline2ATX = loadScript(scriptName)

        when: 'create a testStep'
            Map result = pipeline2ATX.createTestStep(row, false)

        then: 'expect a testStep map'
            getTestStep(false, false) == result
    }

    def 'Create test step folder from row'() {
        // TODO: Add child test blocked by https://github.com/jenkinsci/JenkinsPipelineUnit/issues/337
        given: 'a row with node'
            def node = Mock(FlowNode)
            def row = new FlowGraphTable.Row(node)
            def childRow = new FlowGraphTable.Row(node)
            node.getDisplayName() >> 'testStepFolder'

            row.getNode() >> node
            row.firstTreeChild = null

        when: 'create a testStepFolder'
            Map result = pipeline2ATX.createTestStepFolder(row, false)

        then: 'expect a testStepFolder map'
            result == [:]
    }

    def 'Get log text from pipeline step'() {
        given: 'a node'
            def node = Mock(FlowNode)
            node.getAction(_) >> action
            action.getLogText() >> annotated
            annotated.length() >> logLen
            annotated.writeHtmlTo(_, _) >> 'Test log'


        when: 'get log text of node'
            String text = pipeline2ATX.getLogText(node)

        then: 'expect a log text'
            text == ''

        where:
            // TODO: Add happy path, annotated is still null?
            action                  | annotated                 | logLen
            Mock(LogStorageAction)  | Mock(AnnotatedLargeText)  | 1
            Mock(LogStorageAction)  | Mock(AnnotatedLargeText)  | 0
            Mock(LogStorageAction)  | null                      | 0
            null                    | null                      | 0
    }

    private List getAttributes() {
        def build = getCurrentBuild()

        return [['key':'BUILD_URL', 'value':build.get('url')],
                ['key':'BUILD_ID', 'value':build.get('id').toString()],
                ['key':'testParam', 'value':'241543903']]
    }

    private Map getCurrentBuild() {
        Map build = [:]
        build['id'] = 42
        build['url'] = "https://test:0815/testFolder/testJob/${build.get('id')}/"
        build['rawBuild'] = 'rawBuild'

        return build
    }

    private Map getTestStep(Boolean description, Boolean sibling) {
        Map testStep = [:]
        String text = ""

        testStep.put("@type", "teststep")
        testStep.put("name", "testStep")
        testStep.put("verdict", "PASSED")

        if (description) {
            text = "Test description"
            if (sibling) {
                text = [text, text].join("")
            }
        }
        testStep.put("description", text)

        return testStep
    }
}
