package groovy.testSupport

import com.lesfurets.jenkins.unit.RegressionTest
import spock.lang.Specification

/**
 * A base class for Spock testing using the pipeline helper
 *
 * Based on https://github.com/macg33zr/pipelineUnit - licensed under Apache License 2.0
 */
class PipelineSpockTestBase extends Specification implements RegressionTest {

    /**
     * Delegate to the test helper
     */
    @Delegate PipelineTestHelper pipelineTestHelper

    /**
     * Do the common setup
     */
    def setup() {
        // Set callstacks path for RegressionTest
        callStackPath = 'pipelineTests/groovy/tests/callstacks/'

        // Create and config the helper
        pipelineTestHelper = new PipelineTestHelper()
        pipelineTestHelper.setUp()
    }
}
