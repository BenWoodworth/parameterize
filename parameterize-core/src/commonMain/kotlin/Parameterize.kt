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

@file:JvmMultifileClass
@file:JvmName("ParameterizeKt")

package com.benwoodworth.parameterize

import com.benwoodworth.parameterize.ParameterizeConfiguration.*
import com.benwoodworth.parameterize.ParameterizeScope.DeclaredParameter
import com.benwoodworth.parameterize.ParameterizeScope.Parameter
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.reflect.KProperty

/**
 * Executes the [block] for each combination of arguments, as declared with the [parameter][ParameterizeScope.parameter]
 * functions:
 *
 * ```
 * parameterize {
 *     val letter by parameter('a'..'z')
 *     val primeUnder20 by parameterOf(2, 3, 5, 7, 11, 13, 17, 19)
 *     val computedValue by parameter { slowArgumentsComputation() }
 *
 *     // ...
 * }
 * ```
 *
 * With its default behavior, [parameterize] is strictly an alternative syntax to nested `for` loops, with loop
 * variables defined within the body instead of up front, and without the indentation that's required for additional
 * inner loops. For example, this `sequence` is equivalent to nested `red`/`green`/`blue` `for` loops with the same
 * dependent ranges:
 *
 * ```kotlin
 * val reddishYellows = sequence {
 *     parameterize {
 *         val red by parameter(128..255)
 *         val green by parameter(64..(red - 32))
 *         val blue by parameter(0..(green - 64))
 *
 *         yield(Color(red, green, blue))
 *     }
 * }
 * ```
 *
 * In addition to its default behavior, [parameterize] has a [configuration] with options to decorate its iterations,
 * handle and record failures, and summarize the overall loop execution. The flexibility [parameterize] offers makes it
 * suitable for many different specific use cases. Supported use cases include built in ways to access the named
 * parameter arguments when a failure occurs, recording failures while continuing to the next iteration, and throwing a
 * comprehensive multi-failure with the recorded failures and the arguments when each occurred.
 *
 * ### Restrictions
 *
 * - The parameterized [block] must be deterministic, executing the same way for the same parameter arguments.
 *
 * - Parameter arguments must not be mutated, as they are re-used between iterations, and mutations from previous
 *   iterations could result in different execution, breaking the determinism assumption.
 *
 * - Parameters cannot be declared within another parameter's arguments, such as nesting within a lazy `parameter {}`.
 *
 * - Care should be taken with any asynchronous code, since the order that parameters are used must be the same between
 *   iterations, and all async code must be awaited before the [block] completes.
 *
 * @throws ParameterizeException if the DSL is used incorrectly. (See restrictions)
 */
public inline fun parameterize(
    configuration: ParameterizeConfiguration = ParameterizeConfiguration.default,
    block: ParameterizeScope.() -> Unit
) {
    // Exercise extreme caution modifying this code, since the iterator is sensitive to the behavior of this function.
    // Code inlined from a previous version could have subtly different semantics when interacting with the runtime
    // iterator of a later release, and would be major breaking change that's difficult to detect.

    val iterator = ParameterizeIterator(configuration)

    while (true) {
        val scope = iterator.nextIteration() ?: break

        try {
            scope.block()
        } catch (failure: Throwable) {
            iterator.handleFailure(failure)
        } finally {
            iterator.endIteration()
        }
    }
}

/**
 * Calls [parameterize] with a copy of the [configuration] that has options overridden.
 *
 * @param decorator See [ParameterizeConfiguration.Builder.decorator]
 * @param onFailure See [ParameterizeConfiguration.Builder.onFailure]
 * @param onComplete See [ParameterizeConfiguration.Builder.onComplete]
 *
 * @see parameterize
 */
@Suppress(
    // False positive: onComplete is called in place exactly once through the configuration by the end parameterize call
    "LEAKED_IN_PLACE_LAMBDA", "WRONG_INVOCATION_KIND"
)
public inline fun parameterize(
    configuration: ParameterizeConfiguration = ParameterizeConfiguration.default,
    noinline decorator: suspend DecoratorScope.(iteration: suspend DecoratorScope.() -> Unit) -> Unit = configuration.decorator,
    noinline onFailure: OnFailureScope.(failure: Throwable) -> Unit = configuration.onFailure,
    noinline onComplete: OnCompleteScope.() -> Unit = configuration.onComplete,
    block: ParameterizeScope.() -> Unit
) {
    contract {
        callsInPlace(onComplete, InvocationKind.EXACTLY_ONCE)
    }

    val newConfiguration = ParameterizeConfiguration(configuration) {
        this.decorator = decorator
        this.onFailure = onFailure
        this.onComplete = onComplete
    }

    parameterize(newConfiguration, block)
}

internal class SimpleParameterizeScope internal constructor(
    internal val parameterizeState: ParameterizeState,
) : ParameterizeScope {
    internal var iterationEnded: Boolean = false

    override fun toString(): String =
        parameterizeState.getDeclaredParameters().joinToString(
            prefix = "ParameterizeScope(",
            separator = ", ",
            postfix = ")"
        ) { parameter ->
            "${parameter.property.name} = ${parameter.argument}"
        }

    override fun <T> Parameter<T>.provideDelegate(thisRef: Nothing?, property: KProperty<*>): DeclaredParameter<T> {
        if (iterationEnded) {
            throw ParameterizeException("Cannot declare parameter `${property.name}` after its iteration has ended")
        }

        return parameterizeState.declareParameter(property, arguments)
    }
}
