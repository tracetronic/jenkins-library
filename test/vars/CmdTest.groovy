/*
 * Copyright (c) 2021 - 2023 tracetronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
package vars

import groovy.testSupport.PipelineSpockTestBase

class CmdTest extends PipelineSpockTestBase {

    def cmd

    def setup() {
        cmd = loadScript('vars/cmd.groovy')
    }

    def 'Test Linux command'() {
        setup: 'a Linux environment'
            helper.registerAllowedMethod('isUnix', { return true })

        when: 'command is executed'
            cmd.call('ls')

        then: 'expect Linux Shell is used'
            helper.methodCallCount('sh') == 1
            callStack(2) == "[script:ls, returnStdout:true]"
    }

    def 'Test Windows command'() {
        setup: 'a Windows environment'
            helper.registerAllowedMethod('isUnix', { return false })

        when: 'command is executed'
            cmd.call('dir')

        then: 'expect Windows Batch is used'
            helper.methodCallCount('bat') == 1
            callStack(2) == "[script:@echo off\r\ndir, returnStdout:true]"
    }

    private callStack(int index) {
        return helper.getCallStack()[index].args[0].toString()
    }
}
