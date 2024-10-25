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

import com.benwoodworth.parameterize.ParameterizeConfiguration.*
import com.benwoodworth.parameterize.test.testAll
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.test.*

@Suppress("ClassName")
class ParameterizeConfigurationSpec_decorator {
    private inline fun testParameterize(
        noinline decorator: suspend DecoratorScope.(iteration: suspend DecoratorScope.() -> Unit) -> Unit,
        noinline onFailure: OnFailureScope.(failure: Throwable) -> Unit = {
            recordFailure = true
            breakEarly = true
        },
        noinline onComplete: OnCompleteScope.() -> Unit = ParameterizeConfiguration.default.onComplete,
        block: ParameterizeScope.() -> Unit
    ): Unit =
        parameterize(
            decorator = decorator,
            onFailure = onFailure,
            onComplete = onComplete,
            block = block
        )

    @Test
    fun should_be_invoked_once_per_iteration() {
        var iterationCount = 0
        var timesInvoked = 0

        testParameterize(
            decorator = { iteration ->
                timesInvoked++
                iteration()
            }
        ) {
            val iteration by parameter(0..10)

            iterationCount++
            assertEquals(iterationCount, timesInvoked)
        }
    }

    @Test
    fun failures_within_decorator_should_immediately_terminate_parameterize() {
        class FailureWithinDecorator : Throwable()

        testAll<suspend DecoratorScope.(suspend DecoratorScope.() -> Unit) -> Unit>(
            "Before `iteration()`" to {
                throw FailureWithinDecorator()
            },
            "After `iteration()`" to { iteration ->
                iteration()
                throw FailureWithinDecorator()
            },
        ) { decorator ->
            var iterationCount = 0
            var onFailureInvoked = false
            var onCompleteInvoked = false

            assertFailsWith<FailureWithinDecorator> {
                testParameterize(
                    decorator = { iteration ->
                        iterationCount++
                        decorator(iteration)
                    },
                    onFailure = { onFailureInvoked = true },
                    onComplete = { onCompleteInvoked = true }
                ) {
                    val iteration by parameterOf(1, 2)
                }
            }

            assertEquals(iterationCount, 1, "iterationCount")
            assertFalse(onFailureInvoked, "onFailureInvoked")
            assertFalse(onCompleteInvoked, "onCompleteInvoked")
        }
    }

    /**
     * The decorator scope [RestrictsSuspension], so it shouldn't be possible for code outside the library to suspend
     * without hacking around the type system like this. But a nice error should be provided just in case.
     */
    @Test
    fun suspending_unexpectedly_should_fail() {
        val suspendWithoutResuming: suspend Any.() -> Unit = {
            suspendCoroutineUninterceptedOrReturn { COROUTINE_SUSPENDED }
        }

        val suspendDecoratorWithoutResuming: suspend DecoratorScope.() -> Unit = suspendWithoutResuming

        testAll<suspend DecoratorScope.(suspend DecoratorScope.() -> Unit) -> Unit>(
            "before iteration" to { iteration ->
                suspendDecoratorWithoutResuming()
                iteration()
            },
            "after iteration" to { iteration ->
                iteration()
                suspendDecoratorWithoutResuming()
            },
        ) { decorator ->
            val failure = assertFailsWith<ParameterizeException> {
                testParameterize(
                    decorator = decorator
                ) {}
            }

            assertEquals("Decorator suspended unexpectedly", failure.message, "message")
        }
    }

    @Test
    fun iteration_function_should_return_regardless_of_how_parameterize_block_is_exited() {
        var returned = false

        run exitLoop@{
            parameterize(
                decorator = { iteration ->
                    iteration()
                    returned = true
                }
            ) {
                // A non-local return ensures that all types of loop exits will be handled (with a `finally` block)
                return@exitLoop
            }
        }

        assertTrue(returned, "returned")
    }

    @Test
    fun should_throw_if_iteration_function_is_not_invoked() {
        val exception = assertFailsWith<ParameterizeException> {
            testParameterize(
                decorator = {
                    // not invoked
                }
            ) {}
        }

        assertEquals(
            "Decorator must invoke the iteration function exactly once, but was not invoked",
            exception.message,
            "message"
        )
    }

    @Test
    fun should_throw_if_iteration_function_is_invoked_more_than_once() {
        val exception = assertFailsWith<ParameterizeException> {
            testParameterize(
                decorator = { iteration ->
                    iteration()
                    iteration()
                }
            ) {}
        }

        assertEquals(
            "Decorator must invoke the iteration function exactly once, but was invoked twice",
            exception.message,
            "message"
        )
    }

    @Test
    fun is_first_iteration_should_be_correct() = testAll(
        (1..3)
            .flatMap { listOf(it to "before", it to "after") }
            .map { "in iteration ${it.first}, ${it.second}" to it }
    ) { (inIteration, beforeOrAfter) ->
        val before = beforeOrAfter == "before"
        val after = beforeOrAfter == "after"

        var iterationNumber = 1
        testParameterize(
            decorator = { iteration ->
                val shouldCheck = inIteration == iterationNumber

                if (before && shouldCheck) assertEquals(iterationNumber == 1, isFirstIteration)
                iteration()
                if (after && shouldCheck) assertEquals(iterationNumber == 1, isFirstIteration)

                iterationNumber++
            }
        ) {
            val iteration by parameter(1..3)
        }
    }

    @Test
    fun is_last_iteration_should_be_correct() = testAll(
        (1..3).map { "in iteration $it" to it }
    ) { inIteration ->
        var currentIteration = 1

        testParameterize(
            decorator = { iteration ->
                iteration()

                val shouldCheck = inIteration == currentIteration
                if (shouldCheck) assertEquals(currentIteration == 3, isLastIteration)

                currentIteration++
            }
        ) {
            val iteration by parameter(1..3)
        }
    }

    @Test
    fun is_last_iteration_when_accessed_before_invoking_iteration_should_throw() = testAll(
        (1..3).map { "in iteration $it" to it }
    ) { inIteration ->
        var iterationNumber = 1

        val exception = assertFailsWith<ParameterizeException> {
            testParameterize(
                decorator = { iteration ->
                    if (inIteration == iterationNumber) {
                        isLastIteration
                    }

                    iteration()
                    iterationNumber++
                }
            ) {
                val iteration by parameter(1..3)
            }
        }

        assertEquals(
            "Last iteration cannot be known until after the iteration function is invoked",
            exception.message,
            "message"
        )
    }

    @Test
    fun declaring_parameter_after_iteration_function_should_fail() {
        assertFailsWith<ParameterizeException> {
            lateinit var declareParameter: () -> Unit

            testParameterize(
                decorator = { iteration ->
                    iteration()
                    declareParameter()
                }
            ) {
                declareParameter = {
                    val parameter by parameterOf(Unit)
                }
            }
        }
    }

    /**
     * With testing for example, it's important that any setup code before the iteration can be cleaned up, even if
     * the case is skipped because of a parameter with empty arguments.
     */
    @Test
    fun should_complete_after_a_skip() {
        var finishedDecorator = false

        testParameterize(
            decorator = { iteration ->
                iteration()
                finishedDecorator = true
            }
        ) {
            val skip by parameterOf<Unit>()
        }

        assertTrue(finishedDecorator, "finishedDecorator")
    }
}
