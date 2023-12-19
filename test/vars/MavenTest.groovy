/*
 * Copyright (c) 2021 - 2023 tracetronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
package vars

import groovy.testSupport.PipelineSpockTestBase

class MavenTest extends PipelineSpockTestBase {

    def maven

    def setup() {
        maven = loadScript('vars/maven.groovy')
        helper.registerAllowedMethod('cmd', [String.class], null)
    }

    def 'Get project name'(String path, String mavenTool, String jdkTool, int callCount, int callStackIndex) {
        when: 'Maven is called to parse project name'
            maven.getProjectName("mvn", path, mavenTool, jdkTool)

        then: 'expect Maven command with according expression is invoked'
            printCallStack()
            helper.methodCallCount('cmd') == callCount
            helper.callStack[callStackIndex].args[0].toString().contains("-Dexpression=project.name")

        where:
            path      | mavenTool | jdkTool | callCount | callStackIndex
            'pom.xml' | null      | null    | 1         | 1
            'pom.xml' | 'M3'      | 'JDK8'  | 2         | 4
    }

    def 'Get project version'(String path, String mavenTool, String jdkTool, int callCount, int callStackIndex) {
        when: 'Maven is called to parse project version'
            maven.getProjectVersion("mvn", path, mavenTool, jdkTool)

        then: 'expect Maven command with according expression is invoked'
            printCallStack()
            helper.methodCallCount('cmd') == callCount
            helper.callStack[callStackIndex].args[0].toString().contains("-Dexpression=project.version")

        where:
            path      | mavenTool | jdkTool | callCount | callStackIndex
            'pom.xml' | null      | null    | 1         | 1
            'pom.xml' | 'M3'      | 'JDK8'  | 2         | 4
    }

    def 'Get specific information'(String path, String mavenTool, String jdkTool, int callCount, int callStackIndex) {
        when: 'Maven is called to parse specific information'
            maven.getInfo("project.url", "mvn", path, mavenTool, jdkTool)

        then: 'expect Maven command with according expression is invoked'
            printCallStack()
            helper.methodCallCount('cmd') == callCount
            helper.callStack[callStackIndex].args[0].toString().contains("-Dexpression=project.url")

        where:
            path      | mavenTool | jdkTool | callCount | callStackIndex
            'pom.xml' | null      | null    | 1         | 1
            'pom.xml' | 'M3'      | 'JDK8'  | 2         | 4
    }
}
