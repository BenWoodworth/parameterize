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

import com.benwoodworth.parameterize.ParameterizeConfiguration.OnCompleteScope
import com.benwoodworth.parameterize.ParameterizeConfiguration.OnFailureScope
import com.benwoodworth.parameterize.ParameterizeScope.DeclaredParameter
import com.benwoodworth.parameterize.test.testAll
import kotlin.test.*

@Suppress("ClassName")
class ParameterizeConfigurationSpec_onComplete {
    private inline fun testParameterize(
        noinline onFailure: OnFailureScope.(failure: Throwable) -> Unit = {}, // Continue on failure
        noinline onComplete: OnCompleteScope.() -> Unit,
        block: ParameterizeScope.() -> Unit
    ): Unit =
        parameterize(
            onFailure = onFailure,
            onComplete = onComplete,
            block = block
        )

    @Test
    fun should_be_invoked_once_after_all_iterations() {
        var timesInvoked = 0
        var invokedBeforeLastIteration = false

        testParameterize(
            onComplete = {
                timesInvoked++
            }
        ) {
            val iteration by parameter(0..10)

            if (timesInvoked > 0) {
                invokedBeforeLastIteration = true
            }

            if (iteration == 5 || iteration == 7) {
                fail("Fail on $iteration")
            }
        }

        assertEquals(1, timesInvoked, "Times invoked")
        assertFalse(invokedBeforeLastIteration, "Invoked before last iteration")
    }

    @Test
    fun should_be_invoked_once_after_all_iterations_with_break() {
        var timesInvoked = 0
        var invokedBeforeLastIteration = false
        var failureCount = 0

        testParameterize(
            onFailure = {
                breakEarly = failureCount > 1
            },
            onComplete = {
                timesInvoked++
            }
        ) {
            val iteration by parameter(0..10)

            if (timesInvoked > 0) {
                invokedBeforeLastIteration = true
            }

            if (iteration == 5 || iteration == 7) {
                failureCount++
                fail("Fail on $iteration")
            }
        }

        assertEquals(1, timesInvoked, "Times invoked")
        assertFalse(invokedBeforeLastIteration, "Invoked before last iteration")
    }

    @Test
    fun failures_within_on_complete_should_propagate_out_uncaught() {
        class FailureWithinOnComplete : Throwable()

        assertFailsWith<FailureWithinOnComplete> {
            testParameterize(
                onComplete = {
                    throw FailureWithinOnComplete()
                }
            ) {
            }
        }
    }

    @Test
    fun iteration_count_should_be_correct() {
        var expectedIterationCount = 0L

        testParameterize(
            onComplete = {
                assertEquals(expectedIterationCount, iterationCount)
            }
        ) {
            val iteration by parameter(0..100)

            expectedIterationCount++
        }
    }

    @Test
    fun iteration_count_should_be_correct_with_break() {
        var expectedIterationCount = 0L

        testParameterize(
            onFailure = {
                breakEarly = true
            },
            onComplete = {
                assertEquals(expectedIterationCount, iterationCount)
            }
        ) {
            val iteration by parameter(0..100)

            expectedIterationCount++

            if (iteration == 50) {
                fail()
            }
        }
    }

    @Test
    fun iteration_count_should_be_correct_with_skips() {
        var expectedIterationCount = 0L

        testParameterize(
            onComplete = {
                assertEquals(expectedIterationCount, iterationCount)
            }
        ) {
            val iteration by parameter(0..100)

            expectedIterationCount++

            if (iteration % 3 == 0 && iteration % 7 == 0) {
                val skipWithEmptyParameter by parameterOf<Unit>()
            }
        }
    }

    @Test
    fun skip_count_should_be_correct() {
        var expectedSkipCount = 0L

        testParameterize(
            onComplete = {
                assertEquals(expectedSkipCount, skipCount)
            }
        ) {
            val iteration by parameter(0..100)

            if (iteration % 3 == 0 && iteration % 7 == 0) {
                expectedSkipCount++
                val skipWithEmptyParameter by parameterOf<Unit>()
            }
        }
    }

    @Test
    fun skip_count_should_be_correct_with_break() {
        var expectedSkipCount = 0L

        testParameterize(
            onFailure = {
                breakEarly = true
            },
            onComplete = {
                assertEquals(expectedSkipCount, skipCount)
            }
        ) {
            val iteration by parameter(0..100)

            if (iteration % 3 == 0 && iteration % 7 == 0) {
                expectedSkipCount++
                val skipWithEmptyParameter by parameterOf<Unit>()
            }

            if (iteration == 50) {
                fail()
            }
        }
    }

    @Test
    fun success_count_should_be_correct_with_skips_and_failures() {
        var expectedSuccessCount = 0L

        testParameterize(
            onComplete = {
                assertEquals(expectedSuccessCount, successCount)
            }
        ) {
            val iteration by parameter(0..100)

            if (iteration % 3 == 0) {
                val skipWithEmptyParameter by parameterOf<Unit>()
            }

            if (iteration % 7 == 0) {
                fail()
            }

            expectedSuccessCount++
        }
    }

    @Test
    fun failure_count_should_be_correct() {
        var expectedFailureCount = 0L

        testParameterize(
            onComplete = {
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
    fun completed_early_without_breaking_should_be_false() {
        testParameterize(
            onComplete = {
                assertFalse(completedEarly)
            }
        ) {
            val iteration by parameter(0..100)
        }
    }

    @Test
    fun completed_early_with_break_on_last_iteration_should_be_false() {
        testParameterize(
            onFailure = {
                breakEarly = true
            },
            onComplete = {
                assertFalse(completedEarly)
            }
        ) {
            val iterations = 0..100
            val iteration by parameter(iterations)

            if (iteration == iterations.last) {
                fail("last iteration")
            }
        }
    }

    @Test
    fun completed_early_with_break_before_last_iteration_should_be_true() {
        testParameterize(
            onFailure = {
                breakEarly = true
            },
            onComplete = {
                assertTrue(completedEarly)
            }
        ) {
            val iterations = 0..100
            val iteration by parameter(iterations)

            if (iteration == iterations.last / 2) {
                fail("middle iteration")
            }
        }
    }

    @Test
    fun recorded_failures_should_be_correct() {
        val expectedRecordedFailures = mutableListOf<Pair<Throwable, List<DeclaredParameter<*>>>>()

        var lastIteration = -1
        testParameterize(
            onFailure = { failure ->
                if (lastIteration % 3 == 0) {
                    recordFailure = true
                    expectedRecordedFailures += failure to parameters
                }
            },
            onComplete = {
                val actualRecordedFailures = recordedFailures
                    .map { recordedFailure -> recordedFailure.failure to recordedFailure.parameters }

                assertEquals(expectedRecordedFailures, actualRecordedFailures)
            }
        ) {
            val iterations = 0..100
            val iteration by parameter(iterations)
            lastIteration = iteration

            if (iteration % 2 == 0 || iteration % 3 == 0) {
                fail("$iteration")
            }
        }
    }

    @Test
    fun error_constructor_should_build_error_with_correct_values() = testAll(
        "base values" to OnCompleteScope(
            recordedFailures = emptyList(),
            failureCount = 1,
            skipCount = 1,
            iterationCount = 1,
            completedEarly = false
        ),
        "changed values" to OnCompleteScope(
            recordedFailures = listOf(ParameterizeFailure(Throwable(), emptyList())),
            failureCount = 2,
            skipCount = 2,
            iterationCount = 2,
            completedEarly = true
        )
    ) { scope ->
        val error = with(scope) {
            ParameterizeFailedError()
        }

        assertEquals(scope.recordedFailures, error.recordedFailures, error::recordedFailures.name)
        assertEquals(scope.successCount, error.successCount, error::successCount.name)
        assertEquals(scope.failureCount, error.failureCount, error::failureCount.name)
        assertEquals(scope.completedEarly, error.completedEarly, error::completedEarly.name)
    }
}
