/*
 * Copyright 2024 Ben Woodworth
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.benwoodworth.parameterize

import com.benwoodworth.parameterize.ParameterizeConfiguration.Builder
import com.benwoodworth.parameterize.ParameterizeConfigurationSpec.ParameterizeWithOptionDefault
import com.benwoodworth.parameterize.test.TestAllScope
import com.benwoodworth.parameterize.test.testAll
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.test.*

/**
 * Specifies [ParameterizeConfiguration] creation, and how its options are applied to [parameterize].
 *
 * The behavior of the individual options is specified in their respective `ParameterizeConfigurationSpec_*` classes.
 */
class ParameterizeConfigurationSpec {
    // Keep options sorted by the order they're executed, where relevant
    private val configurationOptions = listOf(
        ConfigurationOption(
            ParameterizeConfiguration::decorator,
            Builder::decorator,
            distinctValue = {},
            parameterizeWithConfigurationAndOptionPassed = { configuration, block ->
                parameterize(configuration, decorator = configuration.decorator) { block() }
            }
        ),
        ConfigurationOption(
            ParameterizeConfiguration::onFailure,
            Builder::onFailure,
            distinctValue = {},
            parameterizeWithConfigurationAndOptionPassed = { configuration, block ->
                parameterize(configuration, onFailure = configuration.onFailure) { block() }
            }
        ),
        ConfigurationOption(
            ParameterizeConfiguration::onComplete,
            Builder::onComplete,
            distinctValue = {},
            parameterizeWithConfigurationAndOptionPassed = { configuration, block ->
                parameterize(configuration, onComplete = configuration.onComplete) { block() }
            }
        ),
    ).map { it.property.name to it }

    private class ConfigurationOption<T>(
        val property: KProperty1<ParameterizeConfiguration, T>,
        val builderProperty: KMutableProperty1<Builder, T>,
        val distinctValue: T,
        val parameterizeWithConfigurationAndOptionPassed: (
            configuration: ParameterizeConfiguration, block: ParameterizeScope.() -> Unit
        ) -> Unit
    ) {
        init {
            // In case compiler optimizations break equality assumptions (e.g. shared empty lambda instances or interned strings)
            check(property.name == builderProperty.name) { "Property name mismatch: ${property.name} != ${builderProperty.name}" }
            check(distinctValue != property.get(ParameterizeConfiguration.default)) { "Distinct value is equal to the default: ${property.name} = $distinctValue" }
            check(distinctValue == distinctValue) { "Distinct value can't be checked by equality: ${property.name} = $distinctValue" }
        }

        override fun toString(): String = property.name

        fun setDistinctValue(builder: Builder): Unit =
            builderProperty.set(builder, distinctValue)
    }

    /**
     * Whether all the options are unchanged from their defaults.
     *
     * This exists instead of implementing [ParameterizeConfiguration.equals] and comparing with
     * [ParameterizeConfiguration.default], since comparing configurations by instance seems to be the precedent set by
     * first party Kotlin libraries (e.g. kotlinx.serialization's `JsonConfiguration`).
     */
    private val ParameterizeConfiguration.hasDefaultOptions: Boolean
        get() = configurationOptions.all { (_, option) ->
            val configuredOption = option.property.get(this)
            val defaultOption = option.property.get(ParameterizeConfiguration.default)

            configuredOption == defaultOption
        }


    @Test
    fun builder_should_apply_options_correctly() = testAll(configurationOptions) { option ->
        val configuration = ParameterizeConfiguration {
            option.setDistinctValue(this)

            assertEquals(option.distinctValue, option.builderProperty.get(this), "Builder value after setting")
        }

        assertEquals(option.distinctValue, option.property.get(configuration), "Configuration value after building")
    }

    @Test
    fun builder_should_copy_from_another_configuration_correctly() = testAll(configurationOptions) { option ->
        val copyFrom = ParameterizeConfiguration {
            option.setDistinctValue(this)
        }

        ParameterizeConfiguration(copyFrom) {
            assertEquals(option.distinctValue, option.builderProperty.get(this))
        }
    }

    @Test
    fun string_representation_should_be_class_name_with_options_listed() = testAll(configurationOptions) { testOption ->
        val configuration = ParameterizeConfiguration {
            testOption.setDistinctValue(this)
        }

        val expected = configurationOptions.joinToString(
            prefix = "${ParameterizeConfiguration::class.simpleName}(",
            separator = ", ",
            postfix = ")"
        ) { (_, option) ->
            "${option.property.name}=${option.property.get(configuration)}"
        }

        assertEquals(expected, configuration.toString())
    }

    /**
     * The parameterize block and `decorator` should execute together, since it intuitively works as if the `decorator`
     * logic were inlined into the block. Logic at the end of the block would still be executed before `onFailure` and
     * `onComplete` are invoked.
     *
     * Having `onFailure` run *after* the `decorator` is also easier to reason about if the failure handler itself
     * throws. The `decorator` has "always completes" semantics, and `onFailure` has "terminates immediately" semantics,
     * and that's only possible if `onFailure` runs after. If `onFailure` were to run immediately for a failure in the
     * parameterize block, then the `decorator`'s semantics with how it interacts with failures wouldn't be so
     * straightforward.
     */
    @Test
    fun options_should_be_executed_in_the_correct_order() {
        val order = mutableListOf<String>()

        // Non-builder constructor so all options must be specified
        val configuration = ParameterizeConfiguration(
            decorator = { iteration ->
                order += "decorator (before)"
                iteration()
                order += "decorator (after)"
            },
            onFailure = { order += "onFailure" },
            onComplete = { order += "onComplete" }
        )

        parameterize(configuration) {
            order += "block"
            fail()
        }

        val expectedOrder = listOf(
            "decorator (before)",
            "block",
            "decorator (after)",
            "onFailure",
            "onComplete"
        )

        assertEquals(order, expectedOrder)
    }

    private fun interface ConfiguredParameterize {
        fun configuredParameterize(configure: Builder.() -> Unit, block: ParameterizeScope.() -> Unit)
    }

    private fun testConfiguredParameterize(test: ConfiguredParameterize.() -> Unit) = testAll(
        "configuration-only overload" to {
            test { configure, block ->
                val configuration = ParameterizeConfiguration { configure() }
                parameterize(configuration, block)
            }
        },
        "options overload with passed arguments" to {
            test { configure, block ->
                val configuration = ParameterizeConfiguration { configure() }

                // Leave arguments unnamed so this call errors when new options are added
                parameterize(
                    ParameterizeConfiguration {}, // Should be redundant, since the passed-in options should be used.
                    configuration.decorator,
                    configuration.onFailure,
                    configuration.onComplete,
                    block
                )
            }
        },
        // Ideally this case would call the options overload with all the options using their default argument, but it's
        // not possible to resolve to it without passing at least one of the options. So instead, add multiple cases
        // such that each option will eventually have a case where its default argument is used.
        // (It is possible to achieve with reflection using `KFunction.callBy()`, but only on the JVM at the moment)
        *configurationOptions.map<_, Pair<String, TestAllScope.() -> Unit>> { (_, option) ->
            "options overload with default arguments taken from the `configuration` (except `$option`)" to {
                test { configure, block ->
                    val configuration = ParameterizeConfiguration { configure() }
                    option.parameterizeWithConfigurationAndOptionPassed(configuration, block)
                }
            }

        }.toTypedArray<Pair<String, TestAllScope.() -> Unit>>()
    )

    private fun interface ParameterizeWithOptionDefault {
        fun parameterizeWithOptionDefault(block: ParameterizeScope.() -> Unit)
    }

    /**
     * Test a configuration option's default behavior.
     *
     * If it's not possible to test the option without changing others from their default, [configure] can be used to
     * change ***only*** those other options. The option under test should not be changed, since it's the default it's
     * changed from that is being evaluated. Otherwise, leave [configure] empty.
     *
     * [parameterizeWithDifferentOptionPassed] is necessary to target the [parameterize] options overload. The lambda
     * should call [parameterize] without passing a configuration, and passing the same options set in [configure]
     * (accessible from the configuration provided to the lambda). And if no options were changed in [configure], at
     * least one different option should still be set from the provided configuration, that way the options overload is
     * used instead of the configuration-only overload.
     */
    private fun testParameterizeWithOptionDefault(
        configure: Builder.() -> Unit,
        parameterizeWithDifferentOptionPassed: (
            configuration: ParameterizeConfiguration,
            block: ParameterizeScope.() -> Unit
        ) -> Unit,
        test: ParameterizeWithOptionDefault.() -> Unit
    ) = testAll(
        "with default from builder" to {
            val configuration = ParameterizeConfiguration { configure() }

            val defaultParameterize = ParameterizeWithOptionDefault { block ->
                parameterize(configuration, block)
            }

            test(defaultParameterize)
        },
        "with default `configuration` argument of configuration-only overload" to {
            val configuration = ParameterizeConfiguration { configure() }

            if (!configuration.hasDefaultOptions) {
                skip("This test case can only be performed with an entirely default configuration, since that's what this overload's default uses")
            }

            val defaultParameterize = ParameterizeWithOptionDefault { block ->
                parameterize(block = block)
            }

            test(defaultParameterize)
        },
        "with default `configuration` argument of options overload" to {
            val configuration = ParameterizeConfiguration { configure() }

            val defaultParameterize = ParameterizeWithOptionDefault { block ->
                parameterizeWithDifferentOptionPassed(configuration, block)
            }

            test(defaultParameterize)
        }
    )

    @Test
    fun decorator_configuration_option_should_be_applied() = testConfiguredParameterize {
        var applied = false

        configuredParameterize({
            decorator = { iteration ->
                applied = true
                iteration()
            }
        }) {
        }

        assertTrue(applied, "applied")
    }

    @Test
    fun decorator_default_should_invoke_iteration_function_once() = testParameterizeWithOptionDefault(
        configure = {},
        parameterizeWithDifferentOptionPassed = { configuration, block ->
            parameterize(onFailure = configuration.onFailure, block = block)
        }
    ) {
        var iterationInvocations = 0

        parameterizeWithOptionDefault {
            iterationInvocations++
        }

        assertEquals(1, iterationInvocations, "iterationInvocations")
    }

    @Test
    fun on_failure_configuration_option_should_be_applied() = testConfiguredParameterize {
        var applied = false

        runCatching {
            configuredParameterize({
                onFailure = { applied = true }
            }) {
                fail()
            }
        }

        assertTrue(applied, "applied")
    }

    @Test
    fun on_failure_default_should_rethrow_the_failure() = testParameterizeWithOptionDefault(
        configure = {},
        parameterizeWithDifferentOptionPassed = { configuration, block ->
            parameterize(decorator = configuration.decorator, block = block)
        }
    ) {
        var iterationCount = 0

        class RethrownFailure : Throwable()

        assertFailsWith<RethrownFailure> {
            parameterizeWithOptionDefault {
                val iteration by parameterOf(1..2)

                iterationCount++
                throw RethrownFailure()
            }
        }

        assertEquals(1, iterationCount, "iterationCount")
    }

    @Test
    fun on_complete_should_be_marked_as_being_called_in_place_exactly_once() {
        // Must be assigned exactly once
        val lateAssignedValue: Unit

        parameterize(
            onComplete = {
                // Compile-time error if Kotlin thinks this could be run more than once
                lateAssignedValue = Unit
            }
        ) {}

        // Compile-time error if Kotlin thinks the variable might not be assigned
        @Suppress("UNUSED_EXPRESSION")
        lateAssignedValue
    }

    @Test
    fun on_complete_configuration_option_should_be_applied() = testConfiguredParameterize {
        var applied = false

        configuredParameterize({
            onComplete = { applied = true }
        }) {
        }

        assertTrue(applied, "applied")
    }

    @Test
    fun on_complete_default_should_throw_ParameterizeFailedError() = testParameterizeWithOptionDefault(
        // Continue on failure, so parameterize doesn't terminate before onComplete gets to run
        configure = { onFailure = {} },
        parameterizeWithDifferentOptionPassed = { configuration, block ->
            parameterize(onFailure = configuration.onFailure, block = block)
        }
    ) {
        assertFailsWith<ParameterizeFailedError> {
            parameterizeWithOptionDefault {
                fail()
            }
        }
    }
}
