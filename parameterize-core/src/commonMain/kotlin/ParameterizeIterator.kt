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

@PublishedApi
internal class ParameterizeIterator {
    private val parameterizeState = ParameterizeState()

    private var breakEarly = false
    private var currentIterationScope: SimpleParameterizeScope? = null // Non-null if afterEach still needs to be called

    /**
     * Signals the start of a new [parameterize] iteration, and returns its scope if there is one.
     */
    @PublishedApi
    internal fun nextIteration(): ParameterizeScope? {
        if (breakEarly || !parameterizeState.hasNextArgumentCombination) {
            return null
        }

        parameterizeState.startNextIteration()
        return SimpleParameterizeScope(parameterizeState).also {
            currentIterationScope = it
        }
    }

    @PublishedApi
    internal fun endIteration() {
        val currentIterationScope = checkNotNull(currentIterationScope) { "${::currentIterationScope.name} was null" }

        currentIterationScope.iterationEnded = true
        this.currentIterationScope = null
    }

    @PublishedApi
    internal fun handleControlFlow(controlFlow: ParameterizeControlFlow): Unit = when {
        controlFlow !is ParameterizeStateControlFlow -> throw controlFlow
        controlFlow.parameterizeState !== parameterizeState -> throw controlFlow

        controlFlow is ParameterizeContinue -> {}
        controlFlow is ParameterizeBreak -> throw controlFlow.cause

        else -> throw ParameterizeException( // KT-59625
            "Expected Parameterize control flow to be " +
                    "${ParameterizeBreak::class.simpleName} or ${ParameterizeContinue::class.simpleName}, " +
                    "but was ${controlFlow::class.simpleName}",
            controlFlow
        )
    }
}
