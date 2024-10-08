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

import com.benwoodworth.parameterize.ParameterizeConfiguration.OnFailureScope
import kotlin.test.*

@Suppress("ClassName")
class ParameterizeConfigurationSpec_onFailure {
    private inline fun testParameterize(
        noinline onFailure: OnFailureScope.(failure: Throwable) -> Unit,
        block: ParameterizeScope.() -> Unit
    ): Unit =
        parameterize(
            onFailure = onFailure,
            onComplete = {}, // Don't throw because of the tested failures
            block = block
        )

    @Test
    fun should_be_invoked_once_per_failure() {
        val failureIterations = listOf(1, 3, 4, 7, 9, 10)

        var currentIteration = -1
        val iterationsInvoked = mutableListOf<Int>()

        testParameterize(
            onFailure = {
                iterationsInvoked += currentIteration
            }
        ) {
            val iteration by parameter(0..10)
            currentIteration = iteration

            if (iteration in failureIterations) {
                fail()
            }
        }

        assertEquals(failureIterations, iterationsInvoked)
    }

    @Test
    fun should_be_invoked_with_the_failure() {
        val failures = List(10) { Throwable(it.toString()) }

        val invokedWithFailures = mutableListOf<Throwable>()

        testParameterize(
            onFailure = { failure ->
                invokedWithFailures += failure
            }
        ) {
            val failure by parameter(failures)

            throw failure
        }

        assertEquals(failures, invokedWithFailures)
    }

    @Test
    fun should_not_continue_if_should_break_is_true() {
        val failureIterations = listOf(1, 3, 4, 7)
        val breakIteration = failureIterations.last()

        var lastIteration = -1

        testParameterize(
            onFailure = {
                breakEarly = (lastIteration == breakIteration)
            }
        ) {
            val iteration by parameter(0..10)
            lastIteration = iteration

            if (iteration in failureIterations) {
                fail()
            }
        }

        assertEquals(breakIteration, lastIteration)
    }

    @Test
    fun failures_within_on_failure_should_propagate_out_uncaught() {
        class FailureWithinOnFailure : Throwable()

        assertFailsWith<FailureWithinOnFailure> {
            testParameterize(
                onFailure = {
                    throw FailureWithinOnFailure()
                }
            ) {
                fail()
            }
        }
    }

    @Test
    fun iteration_count_should_be_correct() {
        var expectedIterationCount = 0L

        testParameterize(
            onFailure = {
                assertEquals(expectedIterationCount, iterationCount)
            }
        ) {
            val iteration by parameter(0..100)

            expectedIterationCount++
            fail(iteration.toString())
        }
    }

    @Test
    fun failure_count_should_be_correct() {
        var expectedFailureCount = 0L

        testParameterize(
            onFailure = {
                assertEquals(expectedFailureCount, failureCount)
            }
        ) {
            val iteration by parameter(0..100)

            if (iteration % 2 == 0 || iteration % 7 == 0) {
                expectedFailureCount++
                fail("iteration $iteration")
            }
        }
    }

    @Test
    fun failure_arguments_should_be_those_from_the_last_iteration() {
        val lastParameterArguments = mutableListOf<Pair<String, *>>()

        testParameterize(
            onFailure = {
                val actualParameterArguments = parameters
                    .map { it.property.name to it.argument }

                assertEquals(lastParameterArguments, actualParameterArguments)
            }
        ) {
            lastParameterArguments.clear()

            val iteration by parameter(0..10)
            lastParameterArguments += "iteration" to iteration

            if (iteration % 2 == 0) {
                val evenIteration by parameterOf(iteration)
                lastParameterArguments += "evenIteration" to evenIteration
            }

            if (iteration % 3 == 0) {
                val threevenIteration by parameterOf(iteration)
                lastParameterArguments += "threevenIteration" to threevenIteration
            }

            fail()
        }
    }

    @Test
    fun failure_arguments_should_include_lazily_used_parameters_that_were_unused_this_iteration() = testParameterize(
        onFailure = {
            val actualUsedParameters = parameters.map { it.property.name }
            assertContains(actualUsedParameters, "letter")
        }
    ) {
        val letter by parameterOf('a', 'b')

        var letterUsedThisIteration = false

        val letterNumber by parameter {
            letterUsedThisIteration = true
            (1..2).map { "$letter$it" }
        }

        // Letter contributes to the failure, even though it wasn't used this iteration
        if (letterNumber == "b2") {
            check(!letterUsedThisIteration) { "Letter was actually used this iteration, so test is invalid" }
            fail()
        }
    }
}
