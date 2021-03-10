/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
package vars

import groovy.testSupport.PipelineSpockTestBase

class TaskTest extends PipelineSpockTestBase {

    def task

    def setup() {
        helper.registerAllowedMethod('powershell', [Map.class], null)
        task = loadScript('vars/task.groovy')
    }

    def 'Test Linux task management'(Map params, int callCount) {
        setup: 'a Linux environment'
            helper.registerAllowedMethod('isUnix', [], { true })

        when: 'task management is called'
            task.call(params)

        then: 'expect Shell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('sh') == callCount
            helper.methodCallCount('isUnix') == callCount
            callStack(0) - params == [:]

        where:
            params                                                                                   | callCount
            [filePath: 'test']                                                                       | 2
            [filePath: 'test', args: ['arg1', 'arg2'], startDir: '/test']                            | 2
            [filePath: 'test', args: ['arg1', 'arg2'], startDir: '/test', restart: false]            | 1
            [filePath: 'test', args: ['arg1', 'arg2'], startDir: '/test', wait: true, restart: true] | 2
    }

    def 'Test Windows task management'(Map params, int callCount) {
        setup: 'a Windows environment'
            helper.registerAllowedMethod('isUnix', [], { false })

        when: 'task management is called'
            task.call(params)

        then: 'expect PowerShell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('powershell') == callCount
            helper.methodCallCount('isUnix') == callCount
            callStack(0) - params == [:]

        where:
            params                                                                                          | callCount
            [filePath: 'test.exe']                                                                          | 2
            [filePath: 'test.exe', args: ['arg1', 'arg2'], startDir: 'c:\\test']                            | 2
            [filePath: 'test.exe', args: ['arg1', 'arg2'], startDir: 'c:\\test', restart: false]            | 1
            [filePath: 'test.exe', args: ['arg1', 'arg2'], startDir: 'c:\\test', wait: true, restart: true] | 2
    }

    def 'Test undefined process file path'() {
        when: 'var is called without required parameters'
            task.call([:])

        then: 'expect error is thrown'
            printCallStack()
            helper.methodCallCount('error') == 1
    }

    def 'Start Linux task'() {
        setup: 'a Linux environment'
            helper.registerAllowedMethod('isUnix', [], { true })

        when: 'start a process'
            task.start('test')

        then: 'expect Shell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('sh') == 1
            helper.methodCallCount('isUnix') == 1
            callStack(2).toString().contains('test')
    }

    def 'Start Windows task'() {
        setup: 'a Windows environment'
            helper.registerAllowedMethod('isUnix', [], { false })

        when: 'start a process'
            task.start('C:\\test.exe')

        then: 'expect PowerShell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('powershell') == 1
            helper.methodCallCount('isUnix') == 1
            callStack(2).toString().contains('Start-Process -FilePath C:\\test.exe')
    }

    def 'Terminate Linux task by name'() {
        setup: 'a Linux environment'
            helper.registerAllowedMethod('isUnix', [], { true })

        when: 'terminate a process'
            task.stop('test')

        then: 'expect Shell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('sh') == 1
            helper.methodCallCount('isUnix') == 1
            callStack(2).toString().contains('killall -9 test')
    }

    def 'Terminate Windows task by name'() {
        setup: 'a Windows environment'
            helper.registerAllowedMethod('isUnix', [], { false })

        when: 'terminate a process'
            task.stop('test.exe')

        then: 'expect PowerShell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('powershell') == 1
            helper.methodCallCount('isUnix') == 1
            callStack(2).toString().contains('Stop-Process -Name test')
    }

    def 'Terminate Linux task by id'() {
        setup: 'a Linux environment'
            helper.registerAllowedMethod('isUnix', [], { true })

        when: 'terminate a process'
            task.stop(123)

        then: 'expect Shell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('sh') == 1
            helper.methodCallCount('isUnix') == 1
            callStack(2).toString().contains('kill 123')
    }

    def 'Terminate Windows task by id'() {
        setup: 'a Windows environment'
            helper.registerAllowedMethod('isUnix', [], { false })

        when: 'terminate a process'
            task.stop(123)

        then: 'expect PowerShell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('powershell') == 1
            helper.methodCallCount('isUnix') == 1
            callStack(2).toString().contains('Stop-Process -Id 123')
    }

    def 'Check for Linux task'() {
        setup: 'a Linux environment'
            helper.registerAllowedMethod('isUnix', [], { true })

        when: 'checking for running process name'
            task.isRunning('test')

        then: 'expect Shell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('sh') == 1
            helper.methodCallCount('isUnix') == 1
            callStack(2).toString().contains('pgrep test')
    }

    def 'Check for Windows task'() {
        setup: 'a Windows environment'
            helper.registerAllowedMethod('isUnix', [], { false })

        when: 'var is called without required parameters'
            task.isRunning('test.exe')

        then: 'expect PowerShell is invoked with given parameters'
            printCallStack()
            helper.methodCallCount('powershell') == 1
            helper.methodCallCount('isUnix') == 1
            callStack(2).toString().contains('Get-Process -Name test')
    }

    private callStack(int index) {
        return helper.getCallStack()[index].args[0]
    }
}
