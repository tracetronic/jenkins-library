/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
import hudson.util.ArgumentListBuilder
import org.apache.commons.io.FilenameUtils

/**
 * Process management for Windows (via Powershell) and Linux (via Shell).
 *
 * @param params
 *      the parameter map
 * @see #call(String, List, String, boolean, boolean)
 */
def call(Map params = [:]) {
    String filePath = params.get('filePath')
    if (filePath == null) {
        error('Parameter \'filePath\' is required!')
        return
    }
    List args = params.get('args', [])
    String startDir = params.get('startDir', '')
    boolean wait = params.get('wait', true)
    boolean restart = params.get('restart', true)

    call(filePath, args, startDir, wait, restart)
}

/**
 * Process management for Windows (via Powershell) and Linux (via Shell).
 *
 * @param filePath
 *      the path of an executable file
 * @param args
 *      the arguments to pass to the process
 * @param startDir
 *      the location that the new process should start in
 * @param wait
 *      wait for the specified process to complete
 * @param restart
 *      stop all running process instances and start a new one
 */
def call(String filePath, List args = [], String startDir = '', boolean wait = true, boolean restart = true) {
    if (restart) {
        def procName = FilenameUtils.getBaseName(filePath)
        stop(procName)
        start(filePath, args, startDir, wait)
    } else {
        start(filePath, args, startDir, wait)
    }
}

/**
 * Starts a new process.
 *
 * @param filePath
 *      the path of an executable file
 * @param args
 *      the arguments to pass to the process
 * @param startDir
 *      the location that the new process should start in
 * @param wait
 *      wait for the specified process to complete
 */
String start(String filePath, List args = [], String startDir = '', boolean wait = true) {
    def result
    if (isUnix()) {
        result = startLinuxTask(filePath, args, startDir, wait)
    } else {
        result = startWindowsTask(filePath, args, startDir, wait)
    }
    return result?.trim()
}

private String startWindowsTask(String filePath, List<String> args = [], String startDir = '', boolean wait = true) {
    ArgumentListBuilder cmd = new ArgumentListBuilder()
    cmd.add('Start-Process')
    cmd.add('-FilePath', filePath)
    if (!args?.isEmpty()) {
        cmd.add('-ArgumentList', args.collect { "\"${it}\"" }.join(','))
    }
    if (!startDir?.isEmpty()) {
        cmd.add('-WorkingDirectory', startDir)
    }
    if (wait) {
        cmd.add('-Wait')
    }
    powershell script: cmd, encoding: 'UTF-8', returnStdout: true
}

private String startLinuxTask(String filePath, List<String> args = [], String startDir = '', boolean wait = true) {
    ArgumentListBuilder cmd = new ArgumentListBuilder()
    if (startDir) {
        cmd.add('cd', startDir, '&&')
    }
    cmd.add(filePath)
    if (args) {
        cmd.add(args)
    }
    if (wait) {
        cmd.add('&&', 'pid=$!', '&&', 'wait', '$pid')
    }
    sh script: cmd, encoding: 'UTF-8', returnStdout: true
}

/**
 * Terminates running processes by matching name.
 *
 * @param name
 *      the names of the processes to stop (.exe extension will be stripped)
 */
String stop(String name) {
    def result
    if (isUnix()) {
        result = stopLinuxTask(name)
    } else {
        result = stopWindowsTask(name)
    }
    return result?.trim()
}

/**
 * Terminates a running process by id.
 *
 * @param pid
 *      the process ID
 */
String stop(int pid) {
    def result
    if (isUnix()) {
        result = stopLinuxTask(pid)
    } else {
        result = stopWindowsTask(pid)
    }
    return result?.trim()
}

private String stopWindowsTask(String name) {
    ArgumentListBuilder cmd = new ArgumentListBuilder()
    cmd.add('Stop-Process')
    cmd.add('-Name', name.endsWith('.exe') ? name.substring(0, name.size() - 4) : name)
    powershell script: cmd, encoding: 'UTF-8', returnStdout: true
}

private String stopWindowsTask(int pid) {
    ArgumentListBuilder cmd = new ArgumentListBuilder()
    cmd.add('Stop-Process')
    cmd.add('-Id', "${pid}")
    powershell script: cmd, encoding: 'UTF-8', returnStdout: true
}

private String stopLinuxTask(String name) {
    ArgumentListBuilder cmd = new ArgumentListBuilder()
    cmd.add('killall', '-9', name)
    sh script: cmd, encoding: 'UTF-8', returnStdout: true
}

private String stopLinuxTask(int pid) {
    ArgumentListBuilder cmd = new ArgumentListBuilder()
    cmd.add('kill', "${pid}")
    sh script: cmd, encoding: 'UTF-8', returnStdout: true
}

/**
 * Checks whether a process is still running.
 *
 * @param name
 *      the name of the process to check
 */
boolean isRunning(String name) {
    def result
    if (isUnix()) {
        result = isLinuxTaskRunning(name)
    } else {
        result = isWindowsTaskRunning(name)
    }
    return result
}

private boolean isLinuxTaskRunning(String name) {
    ArgumentListBuilder cmd = new ArgumentListBuilder()
    cmd.add('pgrep', name)
    def status = sh script: cmd, encoding: 'UTF-8', returnStdout: true
    return status == 0
}

private boolean isWindowsTaskRunning(String name) {
    ArgumentListBuilder cmd = new ArgumentListBuilder()
    cmd.add('$ErrorActionPreference="Stop";')
    cmd.add('Get-Process')
    cmd.add('-Name', name.endsWith('.exe') ? name.substring(0, name.size() - 4) : name)
    def status = powershell script: cmd, encoding: 'UTF-8', returnStdout: true
    return status == 0
}
