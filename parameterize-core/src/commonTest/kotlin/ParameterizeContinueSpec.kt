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

import com.benwoodworth.parameterize.test.parameterizeState
import com.benwoodworth.parameterize.test.probeThrow
import kotlin.test.Test
import kotlin.test.assertIs

class ParameterizeContinueSpec {
// TODO
//
//    /**
//     * [ParameterizeContinue] is thrown when a [Parameter] is declared with no arguments, so should cause it to
//     * immediately continue to the next iteration since there isn't an argument to proceed with.
//     */
//    @Test
//    fun should_cause_parameterize_to_immediately_continue_to_the_next_iteration() {
//        val failedAssertions = mutableListOf<String>()
//
//        runCatching {
//            parameterize(
//                onFailure = { failedAssertions += "onFailure handler should not be invoked" },
//                onComplete = { failedAssertions += "onComplete handler should not be invoked" }
//            ) {
//                val iteration by parameter(1..2)
//                if (iteration == 2) failedAssertions += "Should not continue to iteration 2"
//
//                throw ParameterizeBreak(parameterizeState, ParameterizeException("Stub"))
//            }
//        }
//
//        assertEquals(emptyList(), failedAssertions, "Failed assertions")
//    }
//
//    @Test
//    fun decorator() {
//        val failedAssertions = mutableListOf<String>()
//
//        runCatching {
//            parameterize(
//                onFailure = { failedAssertions += "onFailure handler should not be invoked" },
//                onComplete = { failedAssertions += "onComplete handler should not be invoked" }
//            ) {
//                val iteration by parameter(1..2)
//                if (iteration == 2) failedAssertions += "Should not continue to iteration 2"
//
//                throw ParameterizeBreak(parameterizeState, ParameterizeException("Stub"))
//            }
//        }
//
//        assertEquals(emptyList(), failedAssertions, "Failed assertions")
//    }

    // TODO Valid? Test outer declaring param within inner?
    /**
     * When a different *inner* [parameterize] is misused, its should not cause other *outer* [parameterize] calls to
     * fail, as the *inner* [parameterize] being invalid does not make the *outer* one invalid.
     */
    @Test
    fun when_thrown_from_a_different_parameterize_call_it_should_be_ignored() {
        val probedThrows = mutableListOf<Throwable?>()

        runCatching {
            parameterize {
                val outerScope = this

                probeThrow(probedThrows) {
                    parameterize {
                        throw ParameterizeContinue(outerScope.parameterizeState)
                    }
                }
            }
        }

        assertIs<ParameterizeContinue>(
            probedThrows[0],
            "Inner parameterize should not catch continue for the outer parameterize"
        )
    }
}
