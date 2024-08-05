/*
 * Copyright (c) 2021 - 2023 tracetronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */

import com.cloudbees.workflow.flownode.FlowNodeUtil
import groovy.testSupport.PipelineSpockTestBase
import hudson.console.AnnotatedLargeText
import hudson.model.Build
import hudson.model.Project
import hudson.model.Result
import hudson.model.Run
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction
import org.jenkinsci.plugins.workflow.graph.AtomNode
import org.jenkinsci.plugins.workflow.graph.BlockStartNode
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.jenkinsci.plugins.workflow.support.actions.LogStorageAction
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable
import net.sf.json.JSONArray

class Pipeline2ATXTest extends PipelineSpockTestBase {

    def pipeline2ATX
    def scriptName = 'vars/pipeline2ATX.groovy'

    def setup() {
        pipeline2ATX = loadScript(scriptName)
    }

    def 'Collect build attributes'() {
        given: 'a build'
            def build = GroovyMock(Run) // Run, because Build does not have the method "getAbsoluteUrl"
            build.getDisplayName() >> 'TestBuild'
            build.getAbsoluteUrl() >> 'https://testurl'

            addEnvVar('PRODUCT_NAME', 'testProd')
            addEnvVar('GIT_URL', 'https://mygit/blub')
            addEnvVar('WORKSPACE', 'C:/ws/TestBuild/build42')
            addEnvVar('TEST_LEVEL', 'Unit Test')

        when: 'collect the build attributes'       
            List attributes = pipeline2ATX.getBuildAttributes(build,['GIT_URL':'https://mycustomgit/blub','TOOL_NAME':'test.tool'])
                       

        then: 'expect a attributes list with build information'
            result == attributes

        where:
            result = [['key':'PRODUCT_NAME', 'value':'testProd'],
                      ['key':'GIT_URL', 'value':'https://mycustomgit/blub'],
                      ['key':'JENKINS_PIPELINE', 'value':'TestBuild'],
                      ['key':'JENKINS_URL', 'value':'https://testurl'],
                      ['key':'JENKINS_WORKSPACE', 'value':'C:/ws/TestBuild/build42'],
                      ['key':'TEST_LEVEL', 'value':'Unit Test'],
                      ['key':'TOOL_NAME', 'value':'test.tool']]
    }

    def 'Collect build attributes - missing env vars'() {
        given: 'a build'
            def build = GroovyMock(Run) // Run object, because Build does not have the method "getAbsoluteUrl"
            build.getDisplayName() >> 'TestBuild'
            build.getAbsoluteUrl() >> 'https://testurl'
        when: 'collect the build attributes'
            List attributes = pipeline2ATX.getBuildAttributes(build,[:])

        then: 'expect a attributes list with build information'
            result == attributes

        where:
            result = [['key':'JENKINS_PIPELINE', 'value':'TestBuild'],
                      ['key':'JENKINS_URL', 'value':'https://testurl']]
    }

    def 'Collect build constants'() {
        given: 'a build'
            def build = GroovyMock(Run)
            build.id >> 42
            
            addEnvVar('PRODUCT_VERSION', '1.2.3')
            addEnvVar('GIT_COMMIT', 'gitCommit')
            addEnvVar('EXECUTOR_NUMBER', '0815')
            addEnvVar('NODE_NAME', 'Runner0815')


        when: 'collect the build constants'
            List constants = pipeline2ATX.getBuildConstants(build,['GIT_COMMIT':'customGitCommit','TOOL_NAME':'test.tool'])

        then: 'expect a attributes list with build information'
            result == constants

        where:
            result = [['key':'PRODUCT_VERSION', 'value':'1.2.3'],
                      ['key':'GIT_COMMIT', 'value':'customGitCommit'],
                      ['key':'JENKINS_BUILD_ID', 'value':'42'],
                      ['key':'JENKINS_EXECUTOR_NUMBER', 'value':'0815'],
                      ['key':'JENKINS_NODE_NAME', 'value':'Runner0815'],
                      ['key':'TOOL_NAME', 'value':'test.tool']]
    }

    def 'Calculate metric times - error free and non pushed based build'() {
        given: 'pipeline stages'
            def mockPipelineStages = [
                    ['name': 'stage (Declarative)', 'duration': 250],
                    ['name': 'execution phase', 'duration': 500],
                    ['name': 'stage (Declarative: Post', 'duration': 250]
            ]
            def build = GroovyMock(Run)
            build.getStartTimeInMillis() >> 2000
            build.getTimeInMillis() >> 1000

            def currentBuild = GroovyMock(RunWrapper)
            currentBuild.getBuildCauses('jenkins.branch.BranchEventCause') >> []

            binding.setVariable('currentBuild', currentBuild)

        when: 'calculate time values'
            def timeValues = pipeline2ATX.calculateTime(mockPipelineStages, build)

        then: 'verify calculated time values'
            timeValues.setupDuration == 250
            timeValues.setupPercentage == 24
            timeValues.executionDuration == 500
            timeValues.executionPercentage == 49
            timeValues.teardownDuration == 250
            timeValues.teardownPercentage == 24
            timeValues.queueDuration == 1
            timeValues.totalDuration == 1001
            timeValues.fromCommitToStartTime == null
            timeValues.errorTime == null
    }

    def 'Calculate metric times - Time from commit to start for pushed based builds'() {
        given: 'a build and a mocked time from commit to start'
            def mockPipelineStages = [
                    ['name': 'stage (Declarative)', 'duration': 250],
                    ['name': 'execution phase', 'duration': 500],
                    ['name': 'stage (Declarative: Post', 'duration': 250]
            ]
            def build = GroovyMock(Run)
            def currentBuild = GroovyMock(RunWrapper)


            currentBuild.getBuildCauses('jenkins.branch.BranchEventCause') >> JSONArray.fromObject(['push'])

            def arbitraryCommitToStartTimeValue = 1234
            pipeline2ATX.metaClass.getTimeFromCommitToStart = { building ->
                return arbitraryCommitToStartTimeValue
            }

            binding.setVariable('currentBuild', currentBuild)

        when: 'calculate time from commit to start of pipeline'
            def result = pipeline2ATX.calculateTime(mockPipelineStages, build)

        then: 'commit value included'
            result.fromCommitToStartTime == arbitraryCommitToStartTimeValue
    }

    def 'Calculate metric times - Time until error'() {
        given: 'execution phase stages with error'
            def mockExecutionStages = [
                    ['@type': 'teststepfolder', 'name': 'stage (Build)', 'duration': 1, 'teststeps': [
                            ['@type': 'teststep', 'name': 'echo (Build)', 'verdict': 'PASSED', 'duration': 1]
                    ]],
                    ['@type': 'teststepfolder', 'name': 'stage (Test)', 'duration': 3, 'teststeps': [
                            ['@type': 'teststep', 'name': 'echo (Test)', 'verdict': 'PASSED', 'duration': 3]
                    ]],
                    ['@type': 'teststepfolder', 'name': 'stage (Deploy)', 'duration': 5, 'teststeps': [
                            ['@type': 'teststep', 'name': 'echo (Deploy)', 'verdict': 'ERROR', 'duration': 5]
                    ]]
            ]

        when: 'calculate error time'
            def errorTimeValue = pipeline2ATX.calculateErrorTime(mockExecutionStages)

        then: 'verify calculated error time'
            errorTimeValue == 9
    }

    def 'Collect build constants - missing env vars'() {
        given: 'a build'
            def build = GroovyMock(Run)
            build.id >> 42

        when: 'collect the build constants'
            List constants = pipeline2ATX.getBuildConstants(build,[:])

        then: 'expect a attributes list with build information'
            result == constants

        where:
            result = [['key':'JENKINS_BUILD_ID', 'value':'42']]
    }

    def 'Create json report'() {
        given: 'all needed information to create a report'
            def build = GroovyMock(Run)
            def parent = Mock(Project)
            parent.getDisplayName() >> 'UnitTests'
            parent.getDescription() >> 'My Description'
            build.getParent() >> parent
            build.getTimeInMillis() >> 123456000
            build.getStartTimeInMillis() >> 123457000

            def attributes = [[key: 'testAttr', value: 'testAttrValue']]
            def constants = [[key: 'testConst', value: 'testConstValue']]
            def parameters = [[name: 'testPara', direction: 'OUT', value: 'testParaValue']]
            def teststeps = [['@type':'teststep']]

            helper.registerAllowedMethod('getCurrentResult', [Object], {'SUCCESS'})
            pipeline2ATX = loadScript(scriptName)

        when: 'json string is generated'
            String result = pipeline2ATX.generateJsonReport(build, attributes, constants, teststeps, parameters, logfile)

        then: 'expect to find the values in the json string'
            result.contains('"name": "JenkinsPipeline"')
            result.contains('"@type": "testcase"')
            result.contains('"name": "UnitTests"')  // test testcase name
            result.contains('"verdict": "PASSED"')
            result.contains('"timestamp": 123457000')
            result.contains('"executionTime": 1')
            result.contains('"key": "testAttr"')  // test attributes
            result.contains('"value": "testAttrValue')
            result.contains('"key": "testConst"')  // test constants
            result.contains('"value": "testConstValue')
            result.contains('"@type": "teststep"') // test teststeps
            result.contains('"name": "testPara"') // test parameters
            result.contains('"direction": "OUT"') // test parameters
            result.contains('"value": "testParaValue') //test parameters
            if (logfile) {  // test artifacts
                result.contains('"artifacts":')
                result.contains(logfile)
            }

        where:
            logfile << ["log.file", ""]

    }

    def 'Get current build result'() {
        given: 'a build that will have FAILED result when its in progress and SUCCESS when already finished'
            def build = Mock(Build)
            build.isInProgress() >> inProgress
            build.getResult() >> Result.SUCCESS
            RunWrapper.metaClass.getCurrentResult = {return 'FAILED'}

        when: 'get current build result'
            def result = pipeline2ATX.getCurrentResult(build)

        then: 'expect the current build result depends on progress status'
            result == exectedResult

        where:
            inProgress | exectedResult
            true       | 'FAILED'
            false      | 'SUCCESS'
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

    def 'Map result to test.guide verdict'() {
        expect: 'expect result is mapped to according test.guide verdict'
            verdict == pipeline2ATX.resultToATXVerdict(result)

        where:
            result     | verdict
            'SUCCESS'  | 'PASSED'
            'UNSTABLE' | 'FAILED'
            'FAILED'   | 'ERROR'
            'FAILURE'  | 'ERROR'
            'INVALID'  | 'NONE'
    }

    def 'Get execution steps'() {
        given: 'a list of rows with different display names'
            def build = GroovyMock(WorkflowRun)
            def rows = []
            ['stage', 'stage', 'nostage', 'parallel','stage','test'].each {name ->
                def row = Mock(FlowGraphTable.Row)
                row.getDisplayName() >> name
                rows.add(row)
            }
            FlowGraphTable.metaClass.getRows = {return rows}
            // use the appendLogs flag to change behavior and test different paths of the function
            helper.registerAllowedMethod("crawlRows", [Object, Object, Object], {arg1, arg2, arg3 ->
                if (arg2) {return ['@type':'dummy']}; return [:]
            })
            pipeline2ATX = loadScript(scriptName)

        when: 'list of rows is evaluated'
            def result = pipeline2ATX.getExecutionSteps(build, appendLogs)

        then: 'result has the size of the number of `stage` rows that return a nonempty crawl result'
            result.size() == expectedSize

        where:
            appendLogs << [true, false]
            expectedSize << [3, 0]

    }

    def 'Crawl a row with direct match'() {
        given: 'A row with a specific node type and display name'
            def row = Mock(FlowGraphTable.Row)
            row.getNode() >> node
            row.getDisplayName() >> name

            helper.registerAllowedMethod('createTestStep', [Object, Object], { row1, arg2 ->
                return ['@type': 'teststep', 'name': row1.getDisplayName()]
            })
            helper.registerAllowedMethod('createTestStepFolder', [Object, Object], { row1, arg2 ->
                return ['@type': 'teststepfolder', 'name': row1.getDisplayName()]
            })
            pipeline2ATX = loadScript(scriptName)

        when: 'row is crawled'
            def result = pipeline2ATX.crawlRows(row, false, insideStage)

        then: 'the correct atx item is returned'
            result.size() == 2
            result['name'] == expectedName
            result['@type'] == expectedType

        where:
            node                 | name       | insideStage | expectedName     | expectedType
            Mock(AtomNode)       | "stage"    | true        | "creating stage" | "teststep"
            Mock(BlockStartNode) | "stage"    | true        | "creating stage" | "teststep"
            Mock(AtomNode)       | "parallel" | true        | "parallel"       | "teststep"
            Mock(BlockStartNode) | "stage"    | false       | "stage"          | "teststepfolder"
    }

    def 'Crawl a row - edge cases'() {
        given: 'A undefined row with or without a crawlable child row'
            def outerRow = new FlowGraphTable.Row(Mock(FlowNode))
            def innerRow = Mock(FlowGraphTable.Row)
            innerRow.getNode() >> Mock(AtomNode)
            innerRow.getDisplayName() >> "bat"

            if (stack) {
                outerRow.firstTreeChild = innerRow
            }

            helper.registerAllowedMethod('createTestStep', [Object, Object], { row1, arg2 ->
                return ['@type': 'teststep', 'name': row1.getDisplayName()]
            })
            pipeline2ATX = loadScript(scriptName)

        when: 'row is crawled'
            def result = pipeline2ATX.crawlRows(outerRow, false, false)

        then: 'crawl result of the child row or an empty result is returned'
            result.size() == expectedSize
            if (expectedSize > 0) {
                result['name'] == 'bat'
                result['@type'] == 'teststep'
            }

        where:
            stack << [true, false]
            expectedSize << [2, 0]
    }

    def 'Get description from row'() {
        given: 'a description row with node'
            def node = Mock(FlowNode)
            def row = Mock(FlowGraphTable.Row)
            row.getNode() >> node

            helper.registerAllowedMethod('getLogText', [Object], { return logText })
            pipeline2ATX = loadScript(scriptName)

        when: 'get description of node'
            def description = pipeline2ATX.getDescription(row)

        then: 'expect a string with max length 120 or empty string'
            description.length() <= 120
            result == description

        where:
            logText << ['Short test description', 'x'*150, '']
            result << ['Short test description', 'x'*117+'...', '']
    }

    def 'Get TestStep Name from row'() {
        given: 'A row optionally with arguments'
            def node = Mock(AtomNode)
            def row = new FlowGraphTable.Row(node)
            node.getDisplayFunctionName() >> 'testnode'

            ArgumentsAction.metaClass.static.getStepArgumentsAsString = {FlowNode n -> return arguments}

        when: 'get step name of row'
            String result = pipeline2ATX.getTestStepName(row)

        then: 'result is the expected string'
            result == expectedResult

        where:
            arguments                                       | expectedResult
            ''                                              | 'testnode'
            'short'                                         | 'testnode (short)'
            'x' * 150                                       | 'testnode (' + 'x' * 106 + '...)'
            ' ' * 10 + '\t' * 10 + '\n' * 10 + 'stripped  ' | 'testnode (stripped)'
    }

    def 'Create test step item from row'() {
        given: 'a row with node'
            def node = Mock(AtomNode)
            def row = new FlowGraphTable.Row(node)

            FlowNodeUtil.metaClass.static.getStatus = { FlowNode n -> 'SUCCESS' }
            helper.registerAllowedMethod('getTestStepName', [Object], { return tsName })
            helper.registerAllowedMethod('getDescription', [Object], { return logText })
            pipeline2ATX = loadScript(scriptName)

        when: 'create a testStep'
            Map result = pipeline2ATX.createTestStep(row, addLogs, skipped)

        then: 'expect a testStep map'
            result['@type'] == 'teststep'
            result['name'] == expectedName
            result['verdict'] == expectedVerdict
            result.containsKey('description') == hasDesciption
            if (hasDesciption) {
                result['description'] == logText
            }

        where:
            tsName  | addLogs | skipped | logText         | expectedName        | hasDesciption | expectedVerdict
            "stage" | true    | false   | "this is a log" | "stage"             | true          | 'PASSED'
            "stage" | false   | false   | "this is a log" | "stage"             | false         | 'PASSED'
            "stage" | true    | true    | ""              | "stage --> skipped" | false         | 'NONE'
    }

    def 'Create test step folder from row'() {
        given: 'a row with node'
            def outerNode = Mock(BlockStartNode)
            def outerRow = new FlowGraphTable.Row(outerNode)
            def innerNode = Mock(BlockStartNode)
            def innerRow = new FlowGraphTable.Row(innerNode)
            innerNode.getAction(BodyInvocationAction.class) >> new BodyInvocationAction()

            outerRow.firstTreeChild = innerRow
            innerRow.firstTreeChild = new FlowGraphTable.Row(outerNode)
            if (multiInnerBlock) {
                innerRow.nextTreeSibling = new FlowGraphTable.Row(innerNode)
            }
            helper.registerAllowedMethod("crawlRows", [Object, Object, Object],
                    {arg1, arg2, arg3 -> if (crawlSuccess) {return ['@type':'dummy']} else { return null }})
            helper.registerAllowedMethod('getTestStepName', [Object], { return 'foldertest' })
            helper.registerAllowedMethod('getDescription', [Object], { return logText })
            helper.registerAllowedMethod('createTestStep', [Object, Object, Object],
                    {arg1, arg2, arg3 -> return ['@type':'teststep']})
            pipeline2ATX = loadScript(scriptName)

        when: 'create a testStepFolder'
            Map result = pipeline2ATX.createTestStepFolder(outerRow, appendLogs)

        then: 'expect a testStepFolder map'
            if (crawlSuccess) {
                result['@type'] == 'teststepfolder'
                result['name'] == 'foldertest'
                result['teststeps'].size() == expectedTestSteps
                result.containsKey('description') == expectDesciption
                if (expectDesciption) {
                    result['description'] == logText
                }
            } else {
                result['@type'] == 'teststep'
            }

        where:
            multiInnerBlock | crawlSuccess | logText | appendLogs | expectDesciption | expectedTestSteps
            false           | true         | "log"   | true       | true             | 1
            false           | false        | "log"   | false      | false            | 0
            true            | true         | "log"   | false      | false            | 2
            false           | true         | ""      | true       | false            | 1

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

    private Map getCurrentBuild() {
        Map build = [:]
        build['id'] = 42
        build['url'] = "https://test:0815/testFolder/testJob/${build.get('id')}/"
        build['rawBuild'] = 'rawBuild'

        return build
    }
}
