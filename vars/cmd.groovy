/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */

/**
 * Executes a passed command on Linux Shell or Windows Batch depending on the node.
 *
 * @param cmd
 *      the command to be executed
 */
def call(String cmd) {
    String result
    if (isUnix()) {
        result = sh script: cmd, returnStdout: true
    } else {
        result = bat script: "@echo off\r\n${cmd}", returnStdout: true
    }
    return result
}
