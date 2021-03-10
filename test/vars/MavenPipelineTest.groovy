/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
package vars

import groovy.testSupport.PipelineSpockTestBase

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static com.lesfurets.jenkins.unit.global.lib.ProjectSource.projectSource

class MavenPipelineTest extends PipelineSpockTestBase {

    final static String PIPELINE_ROOT = 'test/resources/vars/maven/'

    def setup() {
        def library = library().name('jenkins-library')
                .defaultVersion('unused')
                .allowOverride(true)
                .implicit(false)
                .targetPath('unused')
                .retriever(projectSource())
                .build()
        helper.registerSharedLibrary(library)
    }

    def 'Test Maven pipeline'() {
        when:
            runScript(PIPELINE_ROOT + 'mavenPipeline.groovy')

        then:
            printCallStack()
            assertJobStatusSuccess()
    }
}
