/*
 * Copyright (c) 2021 - 2023 tracetronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
package vars

import groovy.testSupport.PipelineSpockTestBase

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static com.lesfurets.jenkins.unit.global.lib.ProjectSource.projectSource

class TaskPipelineTest extends PipelineSpockTestBase {

    final static String PIPELINE_ROOT = 'test/resources/vars/task/'

    def setup() {
        def library = library().name('jenkins-library')
                .defaultVersion('unused')
                .allowOverride(true)
                .implicit(false)
                .targetPath('unused')
                .retriever(projectSource())
                .build()
        helper.registerSharedLibrary(library)
        helper.registerAllowedMethod('powershell', [Map.class], null)
    }

    def 'Test valid pipeline'() {
        when:
            runScript(PIPELINE_ROOT + 'validPipeline.groovy')

        then:
            printCallStack()
            assertJobStatusSuccess()
    }

    def 'Test invalid pipeline'() {
        when:
            runScript(PIPELINE_ROOT + 'invalidPipeline.groovy')

        then:
            printCallStack()
            assertJobStatusFailure()
    }

    def 'Start task pipeline'() {
        when:
            runScript(PIPELINE_ROOT + 'startTaskPipeline.groovy')

        then:
            printCallStack()
            assertJobStatusSuccess()
    }

    def 'Stop task pipeline'() {
        when:
            runScript(PIPELINE_ROOT + 'stopTaskPipeline.groovy')

        then:
            printCallStack()
            assertJobStatusSuccess()
    }

    def 'Is running task pipeline'() {
        when:
            runScript(PIPELINE_ROOT + 'isRunningTaskPipeline.groovy')

        then:
            printCallStack()
            assertJobStatusSuccess()
    }
}
