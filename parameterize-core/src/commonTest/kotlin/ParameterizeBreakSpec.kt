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

class ParameterizeBreakSpec {
// TODO Move to configuration module
//
//    /**
//     * [ParameterizeBreak] is thrown when [parameterize] is misused, so should cause it to immediately fail since
//     * its state and parameter tracking are invalid.
//     */
//    @Test
//    fun should_cause_parameterize_to_immediately_break_without_continuing_or_triggering_handlers() {
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
//    fun should_cause_parameterize_to_fail_with_the_break_exception() {
//        val breakException = ParameterizeException("Stub")
//
//        val actualException = assertFailsWith<ParameterizeException> {
//            parameterize {
//                throw ParameterizeBreak(parameterizeState, breakException)
//            }
//        }
//
//        assertSame(breakException, actualException, "Should fail with the break's exception")
//    }
//
//    // TODO Valid? Test outer declaring param within inner?
//    /**
//     * When a different *inner* [parameterize] is misused, its should not cause other *outer* [parameterize] calls to
//     * fail, as the *inner* [parameterize] being invalid does not make the *outer* one invalid.
//     */
//    @Test
//    fun when_thrown_from_a_different_parameterize_call_it_should_be_ignored() {
//        val probedThrows = mutableListOf<Throwable?>()
//
//        runCatching {
//            parameterize {
//                val outerScope = this
//
//                probeThrow(probedThrows) {
//                    parameterize {
//                        throw ParameterizeBreak(outerScope.parameterizeState, ParameterizeException("Stub"))
//                    }
//                }
//            }
//        }
//
//        assertIs<ParameterizeBreak>(probedThrows[0], "Inner parameterize should not catch break for the outer parameterize")
//    }
}
