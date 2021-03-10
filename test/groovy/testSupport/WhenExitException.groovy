package groovy.testSupport

/**
 * An exception class to exit a stage due to the when statement
 *
 * Based on https://github.com/macg33zr/pipelineUnit - licensed under Apache License 2.0
 */
class WhenExitException extends Exception {

    public WhenExitException(String message) {
        super(message)
    }
}
