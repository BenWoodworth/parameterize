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

import com.benwoodworth.parameterize.ParameterizeScope.DeclaredParameter
import kotlin.reflect.KProperty

internal class ParameterizeState {
    internal val parameterizeContinue = ParameterizeContinue(this)

    /**
     * The parameters created for [parameterize].
     *
     * Parameter instances are re-used between iterations, so will never be removed.
     * The true number of parameters in the current iteration is maintained in [parameterCount].
     */
    private val parameters = ArrayList<ParameterState>()
    private var declaringParameter: KProperty<*>? = null
    private var parameterCount = 0

    /**
     * The parameter that will be iterated to the next argument during this iteration.
     *
     * Set to `null` once the parameter is iterated.
     */
    private var parameterToIterate: ParameterState? = null

    /**
     * The last parameter this iteration that has another argument after declaring, or `null` if there hasn't been one yet.
     */
    private var lastParameterWithNextArgument: ParameterState? = null

    private var iterationCount = 0L
    private var skipCount = 0L
    private var failureCount = 0L

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
        property: KProperty<*>,
        arguments: Sequence<T>
    ): DeclaredParameter<T> = trackNestedDeclaration(property) {
        val parameterIndex = parameterCount

        val parameter = if (parameterIndex in parameters.indices) {
            parameters[parameterIndex].apply {
                // If null, then a previous parameter's argument has already been iterated,
                // so all subsequent parameters should be discarded in case they depended on it
                if (parameterToIterate == null) reset()
            }
        } else {
            ParameterState(this)
                .also { parameters += it }
        }

        parameter.declare(property, arguments)
        parameterCount++ // After declaring, since the parameter shouldn't count if declare throws

        if (parameter === parameterToIterate) {
            parameter.nextArgument()
            parameterToIterate = null
        }

        if (!parameter.isLastArgument) {
            lastParameterWithNextArgument = parameter
        }

        @Suppress("UNCHECKED_CAST") // Assuming it's declared like the previous iteration, the argument should still be T
        return parameter.getDeclaredParameter() as DeclaredParameter<T>
    }

    private inline fun <T> trackNestedDeclaration(property: KProperty<*>, block: () -> T): T {
        val outerParameter = declaringParameter
        if (outerParameter != null) {
            val message = "Nesting parameters is not currently supported: " +
                    "`${property.name}` was declared within `${outerParameter.name}`'s arguments"

            throw ParameterizeBreak(this, ParameterizeException(message))
        }

        try {
            declaringParameter = property
            return block()
        } finally {
            declaringParameter = outerParameter
        }
    }

    fun getDeclaredParameters(): List<DeclaredParameter<*>> =
        parameters.take(parameterCount)
            .map { it.getDeclaredParameter() }

    fun checkFailure(failure: Throwable) {
        if (parameterToIterate != null) {
            val message = "Previous iteration executed to this point successfully, " +
                    "but now failed with the same arguments"

            throw ParameterizeException(message, failure)
        }
    }
}
