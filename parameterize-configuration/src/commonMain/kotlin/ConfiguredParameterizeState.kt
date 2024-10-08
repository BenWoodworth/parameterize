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
import com.benwoodworth.parameterize.ParameterizeScope.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.resume
import kotlin.jvm.JvmInline
import kotlin.reflect.KProperty

@PublishedApi
internal class ConfiguredParameterizeState(
    private val configuration: ParameterizeConfiguration
) {
    private var iterationCount = 0L
    private var skipCount = 0L
    private var failureCount = 0L
    private val recordedFailures = mutableListOf<ParameterizeFailure>()

    private var breakEarly = false
    private var currentIterationScope: ConfiguredParameterizeScope? =
        null // Non-null if afterEach still needs to be called
    private var decoratorCoroutine: DecoratorCoroutine? = null

    /**
     * Signals the start of a new [parameterize] iteration, and returns its scope if there is one.
     */
    @PublishedApi
    internal fun nextIteration(parameterizeScope: ParameterizeScope): ParameterizeScope? {
        if (breakEarly) {
            return null
        }

        if (currentIterationScope != null) afterEach()

        return ConfiguredParameterizeScope(parameterizeScope).also {
            currentIterationScope = it
            iterationCount++
            beforeEach()
        }
    }

    @PublishedApi
    internal fun handleFailure(failure: Throwable): Unit = when {
        failure is ParameterizeControlFlow -> {
            afterEach() // Since nextIteration() won't be called again to finalize the iteration
            throw failure // TODO Could be blocked if afterEach throws
        }

        else -> {
            afterEach() // Since the decorator should complete before onFailure is invoked

            val result = handleFailure(configuration.onFailure, failure)
            breakEarly = result.breakEarly
        }
    }

    private fun beforeEach() {
        decoratorCoroutine = DecoratorCoroutine(parameterizeState, configuration)
            .also { it.beforeIteration() }
    }

    private fun afterEach() {
        val currentIterationScope = checkNotNull(currentIterationScope) { "${::currentIterationScope.name} was null" }
        val decoratorCoroutine = checkNotNull(decoratorCoroutine) { "${::decoratorCoroutine.name} was null" }

        currentIterationScope.iterationCompleted = true
        decoratorCoroutine.afterIteration()

        this.currentIterationScope = null
        this.decoratorCoroutine = null
    }

    fun handleContinue() {
        skipCount++
    }

    @JvmInline
    value class HandleFailureResult(val breakEarly: Boolean)

    fun handleFailure(onFailure: OnFailureScope.(Throwable) -> Unit, failure: Throwable): HandleFailureResult {
        failureCount++

        val scope = OnFailureScope(
            state = this,
            iterationCount,
            failureCount,
        )

        with(scope) {
            onFailure(failure)

            if (recordFailure) {
                recordedFailures += ParameterizeFailure(failure, parameters)
            }

            return HandleFailureResult(breakEarly)
        }
    }

    private fun handleComplete() {
        afterEach()

        val scope = OnCompleteScope(
            iterationCount,
            skipCount,
            failureCount,
            completedEarly = hasNextArgumentCombination,
            recordedFailures,
        )

        configuration.onComplete(scope)
    }
}

private class ConfiguredParameterizeScope(
    private val baseScope: ParameterizeScope
) : ParameterizeScope {
    val declaredParameters = mutableListOf<DeclaredParameter<*>>()

    override fun <T> Parameter<T>.provideDelegate(thisRef: Nothing?, property: KProperty<*>): DeclaredParameter<T> =
        with(baseScope) {
            provideDelegate(thisRef, property)
                .also { declaredParameters += it }
        }
}

/**
 * The [decorator][ParameterizeConfiguration.decorator] suspends for the iteration so that the one lambda can be run as
 * two separate parts, without needing to wrap the (inlined) [parameterize] block.
 */
private class DecoratorCoroutine(
    private val parameterizeState: ParameterizeState,
    private val configuration: ParameterizeConfiguration
) {
    private val scope = DecoratorScope(parameterizeState)

    private var continueAfterIteration: Continuation<Unit>? = null
    private var completed = false

    private val iteration: suspend DecoratorScope.() -> Unit = {
        parameterizeState.checkState(continueAfterIteration == null) {
            "Decorator must invoke the iteration function exactly once, but was invoked twice"
        }

        suspendDecorator { continueAfterIteration = it }
        isLastIteration = !parameterizeState.hasNextArgumentCombination
    }

    fun beforeIteration() {
        check(!completed) { "Decorator already completed" }

        val invokeDecorator: suspend DecoratorScope.() -> Unit = {
            configuration.decorator(this, iteration)
        }

        invokeDecorator
            .createCoroutineUnintercepted(
                receiver = scope,
                completion = Continuation(EmptyCoroutineContext) {
                    completed = true
                    it.getOrThrow()
                }
            )
            .resume(Unit)

        parameterizeState.checkState(continueAfterIteration != null) {
            if (completed) {
                "Decorator must invoke the iteration function exactly once, but was not invoked"
            } else {
                "Decorator suspended unexpectedly"
            }
        }
    }

    fun afterIteration() {
        check(!completed) { "Decorator already completed" }

        continueAfterIteration?.resume(Unit)
            ?: error("Iteration not invoked")

        parameterizeState.checkState(completed) {
            "Decorator suspended unexpectedly"
        }
    }
}
