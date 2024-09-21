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
import com.benwoodworth.parameterize.ParameterizeScope.ParameterDelegate
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmInline

internal class ParameterizeState {
    /**
     * The parameters created for [parameterize].
     *
     * Parameter instances are re-used between iterations, so will never be removed.
     * The true number of parameters in the current iteration is maintained in [parameterCount].
     */
    private val parameters = ArrayList<ParameterState<*>>()
    private var parameterCount = 0

    /**
     * The parameter that will be iterated to the next argument during this iteration.
     *
     * Set to `null` once the parameter is iterated.
     */
    private var parameterToIterate: ParameterState<*>? = null

    /**
     * The last parameter this iteration that has another argument after declaring, or `null` if there hasn't been one yet.
     */
    private var lastParameterWithNextArgument: ParameterState<*>? = null

    private var iterationCount = 0L
    private var failureCount = 0L
    private val recordedFailures = mutableListOf<ParameterizeFailure>()

    val hasNextArgumentCombination: Boolean
        get() = lastParameterWithNextArgument != null || iterationCount == 0L

    val isFirstIteration: Boolean
        get() = iterationCount == 1L

    fun startNextIteration() {
        iterationCount++
        parameterCount = 0

        parameterToIterate = lastParameterWithNextArgument
        lastParameterWithNextArgument = null
    }

    fun <T> declareParameter(
        arguments: Sequence<T>
    ): ParameterDelegate<T> {
        val parameterIndex = parameterCount

        val parameter = if (parameterIndex in parameters.indices) {
            parameters[parameterIndex].apply {
                // If null, then a previous parameter's argument has already been iterated,
                // so all subsequent parameters should be discarded in case they depended on it
                if (parameterToIterate == null) reset()
            } as ParameterState<T>
        } else {
            ParameterState<T>().also { parameters += it }
        }

        parameter.declare(arguments)
        parameterCount++ // After declaring, since the parameter shouldn't count if declare throws

        if (parameter === parameterToIterate) {
            parameter.nextArgument()
            parameterToIterate = null
        }

        if (!parameter.isLastArgument) {
            lastParameterWithNextArgument = parameter
        }

        return ParameterDelegate(parameter, parameter.getArgument())
    }

    /**
     * Get a list of used arguments for reporting a failure.
     */
    fun getFailureArguments(): List<ParameterizeFailure.Argument<*>> =
        parameters.take(parameterCount)
            .filter { it.hasBeenUsed }
            .map { it.getFailureArgument() }

    @JvmInline
    value class HandleFailureResult(val breakEarly: Boolean)

    fun handleFailure(onFailure: OnFailureScope.(Throwable) -> Unit, failure: Throwable): HandleFailureResult {
        checkState(parameterToIterate == null, failure) {
            "Previous iteration executed to this point successfully, but now failed with the same arguments"
        }

        failureCount++

        val scope = OnFailureScope(
            state = this,
            iterationCount,
            failureCount,
        )

        with(scope) {
            onFailure(failure)

            if (recordFailure) {
                recordedFailures += ParameterizeFailure(failure, arguments)
            }

            return HandleFailureResult(breakEarly)
        }
    }

    fun handleComplete(onComplete: OnCompleteScope.() -> Unit) {
        contract {
            callsInPlace(onComplete, InvocationKind.EXACTLY_ONCE)
        }

        val scope = OnCompleteScope(
            iterationCount,
            failureCount,
            completedEarly = hasNextArgumentCombination,
            recordedFailures,
        )

        with(scope) {
            onComplete()
        }
    }
}
