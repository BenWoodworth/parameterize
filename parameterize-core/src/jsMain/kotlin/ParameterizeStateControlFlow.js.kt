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

import com.benwoodworth.parameterize.internal.createThrowableSubclassWithoutStack

internal actual class ParameterizeContinue private constructor(
    actual override val parameterizeState: ParameterizeState
) : ParameterizeStateControlFlow() {
    actual override val cause: Nothing?
        get() = null

    actual companion object {
        actual operator fun invoke(
            state: ParameterizeState
        ): ParameterizeContinue {
            return createThrowableSubclassWithoutStack {
                ParameterizeContinue(state)
            }
        }
    }
}


internal actual class ParameterizeBreak private constructor(
    actual override val parameterizeState: ParameterizeState,
    actual override val cause: ParameterizeException,
) : ParameterizeStateControlFlow() {
    actual companion object {
        actual operator fun invoke(
            state: ParameterizeState,
            cause: ParameterizeException,
        ): ParameterizeBreak {
            return createThrowableSubclassWithoutStack {
                ParameterizeBreak(state, cause)
            }
        }
    }
}
