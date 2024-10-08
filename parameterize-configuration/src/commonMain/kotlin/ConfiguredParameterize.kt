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
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public inline fun parameterize(
    configuration: ParameterizeConfiguration,
    block: ParameterizeScope.() -> Unit
) {
    val state = ConfiguredParameterizeState(configuration)

    parameterize {
        val scope = state.nextIteration() ?: return

        try {
            scope.block()
        } catch (failure: Throwable) {
            state.handleFailure(failure)
        }
    }

    state.handleComplete()
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
