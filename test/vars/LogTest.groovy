/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
package vars

import groovy.testSupport.PipelineSpockTestBase

class LogTest extends PipelineSpockTestBase {

    def log

    def setup() {
        helper.registerAllowedMethod('ansiColor', [String.class, Closure.class], { String m, Closure pr -> pr() })
        log = loadScript('vars/log.groovy')
    }

    def 'Log default formatted message to console'() {
        given: 'a log message with default format'
            String message = 'Test default formatted log message.'

        when: 'log default formatted message'
            log.toConsole(message)

        then: 'expect default formatted log in black, normal and without prefix'
            helper.methodCallCount('echo') == 1
            callStack(0) == message
            callStack(2).contains('30m')
            callStack(2).contains('0m')
    }

    def 'Log formatted message to console'() {
        given: 'a formatted log message'
            String message = 'Test formatted log message.'
            String color = 'green'
            String style = 'bold'
            String prefix = 'testPrefix'

        when: 'log formatted message'
            log.toConsole(message, color, style, prefix)

        then: 'expect formatted log in green, bold and with prefix'
            helper.methodCallCount('echo') == 1
            callStack(0) == message
            callStack(2).contains('32m')
            callStack(2).contains('1m')
            callStack(2).contains('testPrefix')
    }

    def 'Log info message to console'() {
        given: 'an information log message'
            String message = 'Test info log message.'

        when: 'log info message'
            log.info(message)

        then: 'expect information log in cyan'
            helper.methodCallCount('echo') == 1
            callStack(0) == message
            callStack(2).contains('36m')
            callStack(2).contains('[TT] INFO:')
    }

    def 'Log debug message to console'() {
        given: 'a debug log message'
            String message = 'Test debug log message.'
            log.binding.setVariable('env', ['LOG_DEBUG': logDebug])

        when: 'log debug message'
            log.debug(message)

        then: 'expect debug log in blue'
            helper.methodCallCount('echo') == echoCalls
            if (echoCalls) {
                callStack(0) == message
                callStack(2).contains('34m')
                callStack(2).contains('[TT] DEBUG:')
            }

        where: 'different log variable is set'
            logDebug | echoCalls
            'false'  | 0
            'true'   | 1
    }

    def 'Log warning message to console'() {
        given: 'a warning log message'
            String message = 'Test warning log message.'

        when: 'log warning message'
            log.warn(message)

        then: 'expect warning log in yellow'
            helper.methodCallCount('echo') == 1
            callStack(0) == message
            callStack(2).contains('33m')
            callStack(2).contains('[TT] WARN:')
    }

    def 'Log error message to console'() {
        given: 'an error log message'
            String message = 'Test error log message.'
            // Overwrite mocked method from BasePipelineTest
            helper.registerAllowedMethod('error', [String.class], { String m ->
                log.toConsole(m, 'red', 'normal', '[TT] ERROR: ')
            })

        when: 'log error message'
            log.error(message)

        then: 'expect error log in red'
            helper.methodCallCount('echo') == 1
            callStack(0) == message
            callStack(3).contains('31m')
            callStack(3).contains('[TT] ERROR:')
    }

    def 'Unknown style'() {
        given: 'an unknown style argument'
            String message = 'Unknown style'
            String style = 'test'
            String color = 'red'

        when:
            log.toConsole(message, color, style)

        then: 'expect warning about unknown style and set to normal'
            helper.methodCallCount('echo') == 2
            callStack(0) == message
            callStack(1) == "WARN: Unknown font style '${style}', using default now."
            callStack(3).contains('0m')
            callStack(3).contains('31m')
    }

    def 'Unknown color'() {
        given: 'an unknown color argument'
            String message = 'Unknown color'
            String style = 'normal'
            String color = 'test'

        when:
            log.toConsole(message, color, style)

        then: 'expect warning about unknown color and set to black'
            helper.methodCallCount('echo') == 2
            callStack(0) == message
            callStack(1) == "WARN: Unknown font color '${color}', using default now."
            callStack(3).contains('0m')
            callStack(3).contains('39m')
    }

    private callStack(int index) {
        return helper.getCallStack()[index].args[0].toString()
    }
}
