package com.benwoodworth.parameterize

import com.benwoodworth.parameterize.ParameterizeConfiguration.OnCompleteScope
import com.benwoodworth.parameterize.ParameterizeConfiguration.OnFailureScope
import com.benwoodworth.parameterize.ParameterizeScope.ParameterDelegate
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty

internal class ParameterizeState {
    /**
     * The parameters created for [parameterize].
     *
     * Parameter instances are re-used between iterations, so will never be removed.
     * The true number of parameters in the current iteration is maintained in [parameterCount].
     */
    private val parameters = ArrayList<ParameterDelegate<Nothing>>()
    private val parametersUsed = ArrayList<ParameterDelegate<*>>()
    private var parameterBeingUsed: KProperty<*>? = null

    private var parameterCount = 0
    private var parameterCountAfterAllUsed = 0

    private var iterationCount = 0L
    private var failureCount = 0L
    private val recordedFailures = mutableListOf<ParameterizeFailure>()

    private var breakEarly = false
    private var hasNextIteration = true

    /**
     * Starts the next iteration, or returns `false` if there isn't one.
     */
    fun startNextIteration(): Boolean {
        hasNextIteration = iterationCount == 0L || nextArgumentPermutationOrFalse()

        val shouldContinue = hasNextIteration && !breakEarly
        if (shouldContinue) iterationCount++

        return shouldContinue
    }

    fun <T> declareParameter(property: KProperty<T>, arguments: Iterable<T>): ParameterDelegate<Nothing> {
        parameterBeingUsed.let {
            if (it != null) throw ParameterizeException("Nesting parameters is not currently supported: `${property.name}` was declared within `${it.name}`'s arguments")
        }

        val parameterIndex = parameterCount++

        val parameter = if (parameterIndex in parameters.indices) {
            parameters[parameterIndex]
        } else {
            ParameterDelegate<Nothing>(ParameterState())
                .also { parameters += it }
        }

        parameter.parameterState.declare(property, arguments)

        return parameter
    }

    private inline fun <T> KProperty<T>.trackNestedUsage(block: () -> T): T {
        val previousParameterBeingUsed = parameterBeingUsed
        parameterBeingUsed = this

        try {
            return block()
        } finally {
            parameterBeingUsed = previousParameterBeingUsed
        }
    }

    fun <T> getParameterArgument(parameter: ParameterDelegate<*>, property: KProperty<T>): T {
        val isFirstUse = !parameter.parameterState.hasBeenUsed

        return property
            .trackNestedUsage {
                parameter.parameterState.getArgument(property)
            }
            .also {
                if (isFirstUse) trackUsedParameter(parameter)
            }
    }

    private fun trackUsedParameter(parameter: ParameterDelegate<*>) {
        parametersUsed += parameter

        if (!parameter.parameterState.isLastArgument) {
            parameterCountAfterAllUsed = parameterCount
        }
    }

    /**
     * Iterate the last parameter (by the order they're first used) that has a
     * next argument, and reset all parameters that were first used after it
     * (since they may depend on the now changed value, and may be computed
     * differently).
     *
     * Returns `true` if the arguments are at a new permutation.
     */
    private fun nextArgumentPermutationOrFalse(): Boolean {
        var iterated = false

        val usedParameterIterator = parametersUsed
            .listIterator(parametersUsed.lastIndex + 1)

        while (usedParameterIterator.hasPrevious()) {
            val parameter = usedParameterIterator.previous()

            if (!parameter.parameterState.isLastArgument) {
                parameter.parameterState.nextArgument()
                iterated = true
                break
            }

            usedParameterIterator.remove()
            parameter.parameterState.reset()
        }

        for (i in parameterCountAfterAllUsed..<parameterCount) {
            val parameter = parameters[i]

            if (!parameter.parameterState.hasBeenUsed) {
                parameter.parameterState.reset()
            }
        }

        parameterCount = 0
        parameterCountAfterAllUsed = 0

        return iterated
    }

    /**
     * Get a list of used arguments for reporting a failure.
     */
    fun getFailureArguments(): List<ParameterizeFailure.Argument<*>> =
        parameters.take(parameterCount)
            .filter { it.parameterState.hasBeenUsed }
            .mapNotNull { it.parameterState.getFailureArgumentOrNull() }

    fun handleFailure(onFailure: OnFailureScope.(Throwable) -> Unit, failure: Throwable) {
        failureCount++

        val scope = OnFailureScope(
            state = this,
            iterationCount,
            failureCount,
        )

        with(scope) {
            onFailure(failure)

            this@ParameterizeState.breakEarly = breakEarly

            if (recordFailure) {
                recordedFailures += ParameterizeFailure(failure, arguments)
            }
        }
    }

    fun handleComplete(onComplete: OnCompleteScope.() -> Unit) {
        contract {
            callsInPlace(onComplete, InvocationKind.EXACTLY_ONCE)
        }

        val scope = OnCompleteScope(
            iterationCount,
            failureCount,
            completedEarly = hasNextIteration,
            recordedFailures,
        )

        with(scope) {
            onComplete()
        }
    }
}
