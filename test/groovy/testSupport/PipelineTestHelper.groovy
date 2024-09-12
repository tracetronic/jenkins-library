package groovy.testSupport

import com.lesfurets.jenkins.unit.BasePipelineTest

import static com.lesfurets.jenkins.unit.MethodSignature.method

/**
 * Based on https://github.com/macg33zr/pipelineUnit - licensed under Apache License 2.0
 */
class PipelineTestHelper extends BasePipelineTest {

    /**
     * Override the setup for our purposes
     */
    @Override
    void setUp() {

        // Scripts (Jenkinsfiles etc) loaded from root of project directory and have no extension by default
        helper.scriptRoots = ['']
        helper.scriptExtension = ''

        // Add support to the helper to unregister a method
        helper.metaClass.unRegisterAllowedMethod = { String name, List<Class> args ->
            allowedMethodCallbacks.remove(method(name, args.toArray(new Class[args.size()])))
        }

        // Setup the parent stuff
        super.setUp()

        // Declaring all my stuff
        registerDeclarativeMethods()
        registerScriptedMethods()
        setJobVariables()
    }

    /**
     * Declarative pipeline methods not in the base
     *
     * See here:
     * https://www.cloudbees.com/sites/default/files/declarative-pipeline-refcard.pdf
     */
    void registerDeclarativeMethods() {

        // For execution of the pipeline
        helper.registerAllowedMethod('execute', [], {})
        helper.registerAllowedMethod('pipeline', [Closure.class], null)
        helper.registerAllowedMethod('options', [Closure.class], null)

        // Handle endvironment section adding the env vars
        helper.registerAllowedMethod('environment', [Closure.class], { Closure c ->

            def envBefore = [env: binding.getVariable('env')]
            println "Env section - original env vars: ${envBefore.toString()}"
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.delegate = envBefore
            c()

            def envNew = envBefore.env
            envBefore.each { k, v ->
                if (k != 'env') {
                    envNew["$k"] = v
                }

            }
            println "Env section - env vars set to: ${envNew.toString()}"
            binding.setVariable('env', envNew)
        })

        // Handle parameters section adding the default params
        helper.registerAllowedMethod('parameters', [Closure.class], { Closure parametersBody ->

            // Register the contained elements
            helper.registerAllowedMethod('string', [Map.class], { Map stringParam ->

                // Add the param default for a string
                addParam(stringParam.name, stringParam.defaultValue)

            })
            helper.registerAllowedMethod('booleanParam', [Map.class], { Map boolParam ->
                // Add the param default for a string
                addParam(boolParam.name, boolParam.defaultValue.toString().toBoolean())
            })

            // Run the body closure
            def paramsResult = parametersBody()

            // Unregister the contained elements
            helper.unRegisterAllowedMethod('string', [Map.class])
            helper.unRegisterAllowedMethod('booleanParam', [Map.class])

            // Result to higher level. Is this needed?
            return paramsResult
        })

        // If any of these need special handling, it needs to be implemented here or in the tests with a closure instead of null
        helper.registerAllowedMethod('triggers', [Closure.class], null)
        helper.registerAllowedMethod('pollSCM', [String.class], null)
        helper.registerAllowedMethod('cron', [String.class], null)

        helper.registerAllowedMethod('agent', [Closure.class], null)
        helper.registerAllowedMethod('label', [String.class], null)
        helper.registerAllowedMethod('docker', [String.class], null)
        helper.registerAllowedMethod('image', [String.class], null)
        helper.registerAllowedMethod('args', [String.class], null)
        helper.registerAllowedMethod('dockerfile', [Closure.class], null)
        helper.registerAllowedMethod('dockerfile', [Boolean.class], null)

        helper.registerAllowedMethod('timestamps', [], null)
        helper.registerAllowedMethod('tools', [Closure.class], null)
        helper.registerAllowedMethod('stages', [Closure.class], null)
        helper.registerAllowedMethod('validateDeclarativePipeline', [String.class], null)

        helper.registerAllowedMethod('parallel', [Closure.class], null)

        /**
         * Handling of a stage skipping execution in tests due to failure, abort, when
         */
        helper.registerAllowedMethod('stage', [String.class, Closure.class], { String stgName, Closure body ->

            // Returned from the stage
            def stageResult

            // Handling of the when.
            helper.registerAllowedMethod('when', [Closure.class], { Closure whenBody ->

                // Handle a when expression
                helper.registerAllowedMethod('expression', [Closure.class], { Closure expressionBody ->

                    // Run the expression and return any result
                    def expressionResult = expressionBody()
                    if (expressionResult == false) {
                        throw new WhenExitException("Stage '${stgName}' skipped due to when expression returned false")
                    }
                    return expressionResult
                })

                // Handle a when branch
                helper.registerAllowedMethod('branch', [String.class], { String input ->

                    String branchPattern = convertPatternToRegex(input)
                    def patternResult = (binding.getVariable('BRANCH_NAME') ==~ /${branchPattern}/)
                    if (patternResult == false) {
                        throw new WhenExitException("Stage '${stgName}' skipped due to when branch is false")
                    }
                    return patternResult
                })

                // Handle a when buildingTag
                helper.registerAllowedMethod('buildingTag', [], {

                    String tagName = binding.getVariable('TAG_NAME')
                    if (tagName == null || tagName == '') {
                        throw new WhenExitException("Stage '${stgName}' skipped due to not building a tag")
                    }
                    return true
                })

                // Handle a when tag
                helper.registerAllowedMethod('tag', [String.class], { String input ->

                    String tagPattern = convertPatternToRegex(input)
                    String tagName = binding.getVariable('TAG_NAME')
                    if (tagName != null && tagName != '') {
                        def patternResult = (tagName ==~ /${tagPattern}/)
                        if (patternResult == true) {
                            return true
                        }
                    }
                    throw new WhenExitException("Stage '${stgName}' skipped due to no building a specific tag")
                })

                // Handle a when beforeAgent
                helper.registerAllowedMethod('beforeAgent', [Boolean.class], null)

                // Handle a when beforeInput
                helper.registerAllowedMethod('beforeInput', [Boolean.class], null)

                // Handle the boolean when cases without actually checking them
                helper.registerAllowedMethod('not', [Closure.class], null)
                helper.registerAllowedMethod('allOf', [Closure.class], null)
                helper.registerAllowedMethod('anyOf', [Closure.class], null)


                // TODO - handle other when clauses in the when
                // environment : 'when { environment name: 'DEPLOY_TO', value: 'production' }'

                // Run the when body and return any result
                return whenBody()
            })

            // Stage is not executed if build fails or aborts
            def status = binding.getVariable('currentBuild').result
            switch (status) {
                case 'FAILURE':
                case 'ABORTED':
                    println "Stage '${stgName}' skipped - job status: '${status}'"
                    break
                default:

                    // Run the stage body. A when statement may exit with an exception
                    try {
                        stageResult = body()
                    }
                    catch (WhenExitException we) {
                        // The when exited with an exception due to returning false. Swallow it.
                        println we.getMessage()
                    }
                    catch (Exception e) {
                        // Some sort of error in the pipeline
                        throw e
                    }

            }

            // Unregister
            helper.unRegisterAllowedMethod('when', [Closure.class])
            helper.unRegisterAllowedMethod('expression', [Closure.class])
            helper.unRegisterAllowedMethod('branch', [Closure.class])
            helper.unRegisterAllowedMethod('buildingTag', [Closure.class])
            helper.unRegisterAllowedMethod('tag', [Closure.class])
            helper.unRegisterAllowedMethod('beforeAgent', [Closure.class])
            helper.unRegisterAllowedMethod('beforeInput', [Closure.class])
            helper.unRegisterAllowedMethod('not', [Closure.class])
            helper.unRegisterAllowedMethod('allOf', [Closure.class])
            helper.unRegisterAllowedMethod('anyOf', [Closure.class])

            return stageResult
        })

        helper.registerAllowedMethod('steps', [Closure.class], null)
        helper.registerAllowedMethod('script', [Closure.class], null)

        helper.registerAllowedMethod('when', [Closure.class], null)
        helper.registerAllowedMethod('expression', [Closure.class], null)
        helper.registerAllowedMethod('post', [Closure.class], null)

        /**
         * Handling the post sections
         */
        def postResultEmulator = { String section, Closure c ->

            def currentBuild = binding.getVariable('currentBuild')
            def previousBuild = binding.getVariable('currentBuild').previousBuild

            switch (section) {
                case 'always':
                case 'cleanup':
                    return c.call()
                case 'changed':
                    if (currentBuild.result != previousBuild.result) {
                        return c.call()
                    } else {
                        println "post ${section} skipped as not CHANGED"; return null
                    }
                    break
                case 'fixed':
                    if (currentBuild.result == 'SUCCESS' &&
                            (previousBuild.result == 'FAILED' ||
                                    previousBuild.result == 'UNSTABLE')) {
                        return c.call()
                    } else {
                        println "post ${section} skipped as not FIXED"; return null
                    }
                    break
                case 'success':
                    if (currentBuild.result == 'SUCCESS') {
                        return c.call()
                    } else {
                        println "post ${section} skipped as not SUCCESS"; return null
                    }
                    break
                case 'regression':
                    if (previousBuild.result == 'SUCCESS' &&
                            (currentBuild.result == 'UNSTABLE' ||
                                    currentBuild.result == 'FAILURE' ||
                                    currentBuild.result == 'ABORTED')) {
                        return c.call()
                    } else {
                        println "post ${section} skipped as not UNSTABLE, FAILURE or ABORTED after SUCCESS"; return null
                    }
                    break
                case 'unsuccessful':
                    if (currentBuild.result != 'SUCCESS') {
                        return c.call()
                    } else {
                        println "post ${section} skipped as SUCCESS"; return null
                    }
                    break
                case 'unstable':
                    if (currentBuild.result == 'UNSTABLE') {
                        return c.call()
                    } else {
                        println "post ${section} skipped as SUCCESS"; return null
                    }
                    break
                case 'failure':
                    if (currentBuild.result == 'FAILURE') {
                        return c.call()
                    } else {
                        println "post ${section} skipped as not FAILURE"; return null
                    }
                    break
                case 'aborted':
                    if (currentBuild.result == 'ABORTED') {
                        return c.call()
                    } else {
                        println "post ${section} skipped as not ABORTED"; return null
                    }
                    break
                default:
                    assert false, "post section ${section} is not recognised. Check pipeline syntax."
                    break
            }
        }
        helper.registerAllowedMethod('always', [Closure.class], postResultEmulator.curry('always'))
        helper.registerAllowedMethod('changed', [Closure.class], postResultEmulator.curry('changed'))
        helper.registerAllowedMethod('fixed', [Closure.class], postResultEmulator.curry('fixed'))
        helper.registerAllowedMethod('regression', [Closure.class], postResultEmulator.curry('regression'))
        helper.registerAllowedMethod('aborted', [Closure.class], postResultEmulator.curry('aborted'))
        helper.registerAllowedMethod('failure', [Closure.class], postResultEmulator.curry('failure'))
        helper.registerAllowedMethod('success', [Closure.class], postResultEmulator.curry('success'))
        helper.registerAllowedMethod('unstable', [Closure.class], postResultEmulator.curry('unstable'))
        helper.registerAllowedMethod('unsuccessful', [Closure.class], postResultEmulator.curry('unsuccessful'))
        helper.registerAllowedMethod('cleanup', [Closure.class], postResultEmulator.curry('cleanup'))
    }

    /**
     * Scripted pipeline methods not in the base
     */
    void registerScriptedMethods() {

        /**
         * In minutes:
         * timeout(20) {}*/
        helper.registerAllowedMethod('timeout', [Integer.class, Closure.class], null)

        helper.registerAllowedMethod('waitUntil', [Closure.class], null)
        helper.registerAllowedMethod('writeFile', [Map.class], null)
        helper.registerAllowedMethod('build', [Map.class], null)
        helper.registerAllowedMethod('tool', [Map.class], { t -> "${t.name}_HOME" })

        helper.registerAllowedMethod('withCredentials', [Map.class, Closure.class], null)
        helper.registerAllowedMethod('withCredentials', [List.class, Closure.class], null)
        helper.registerAllowedMethod('usernamePassword', [Map.class], { creds -> return creds })

        helper.registerAllowedMethod('deleteDir', [], null)
        helper.registerAllowedMethod('pwd', [], { 'workspaceDirMocked' })

        helper.registerAllowedMethod('stash', [Map.class], null)
        helper.registerAllowedMethod('unstash', [Map.class], null)

        helper.registerAllowedMethod('checkout', [Closure.class], null)

        helper.registerAllowedMethod('withEnv', [List.class, Closure.class], { List list, Closure c ->

            list.each {
                //def env = helper.get
                def item = it.split('=')
                assert item.size() == 2, "withEnv list does not look right: ${list.toString()}"
                addEnvVar(item[0], item[1])
                c.delegate = binding
                c.call()
            }
        })


    }

    /**
     * Variables that Jenkins expects
     */
    void setJobVariables() {

        /**
         * Job params - may need to override in specific tests
         */
        binding.setVariable('params', [:])

        /**
         * The currentBuild in the job
         * Includes a previous build which finished successfully
         */
        def previous = new Expando(result: 'SUCCESS', displayName: 'Build #1233')
        binding.setVariable('currentBuild', new Expando(result: 'SUCCESS', displayName: 'Build #1234', previousBuild: previous))

        /**
         * agent any
         * agent none
         */
        binding.setVariable('any', {})
        binding.setVariable('none', {})

        /**
         * checkout scm
         */
        binding.setVariable('scm', {})

        /**
         * PATH
         */
        binding.setVariable('PATH', '/some/path')

        /**
         * BRANCH_NAME && TAG_NAME
         */
        binding.setVariable('BRANCH_NAME', 'master')
        binding.setVariable('TAG_NAME', null)

        /**
         * Initialize a basic Env passed in from Jenkins - may need to override in specific tests
         */
        addEnvVar('BUILD_NUMBER', '1234')
        addEnvVar('PATH', '/some/path')
    }

    /**
     * Prettier print of call stack to whatever taste
     */
    @Override
    void printCallStack() {
        println '>>>>>> pipeline call stack -------------------------------------------------'
        super.printCallStack()
        println ''
    }

    /**
     * Helper for adding a environment value in tests
     */
    void addEnvVar(String name, String val) {
        if (!binding.hasVariable('env')) {
            binding.setVariable('env', new Expando(getProperty: { p -> this[p] }, setProperty: { p, v -> this[p] = v }))
        }
        def env = binding.getVariable('env') as Expando
        env[name] = val
    }

    /**
     * Helper for turning a GLOB-style matcher into a java Matcher
     */
    def convertPatternToRegex(String pattern) {
        return pattern.replaceAll('\\*', '.*')
    }
}
