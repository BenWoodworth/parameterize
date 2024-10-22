package com.benwoodworth.parameterize

import com.benwoodworth.parameterize.test.probeThrow
import kotlin.test.*

/**
 * Ways that [parameterize] can be checked for misuse, and the [ParameterizeException]s that should be thrown as a
 * result.
 *
 * If the misuse occurs during an iteration, the exception should be thrown within a [ParameterizeBreak] to signal that
 * the [parameterize] loop is in an invalid state, and also to protecting the exception from being caught since thrown
 * [ParameterizeControlFlow]s being caught is documented as being disallowed.
 */
class ParameterizeMisuseSpec {
    @Test
    fun parameter_disappears_on_second_iteration_due_to_external_condition() {
        val probedThrows = mutableListOf<Throwable?>()

        runCatching {
            var shouldDeclareA = true

            parameterize {
                if (shouldDeclareA) {
                    val a by parameterOf(1)
                }

                probeThrow(probedThrows) {
                    val b by parameterOf(1, 2)
                }

                shouldDeclareA = false
            }
        }

        val probedBreak = assertIs<ParameterizeBreak>(probedThrows[0])
        assertEquals("Expected to be declaring `a`, but got `b`", probedBreak.cause.message)
    }

    @Test
    fun parameter_appears_on_second_iteration_due_to_external_condition() {
        var shouldDeclareA = false
        val probedThrows = mutableListOf<Throwable?>()

        runCatching {
            parameterize {
                if (shouldDeclareA) {
                    probeThrow(probedThrows) {
                        val a by parameterOf(2)
                    }
                }

                val b by parameterOf(1, 2)

                shouldDeclareA = true
            }
        }

        val probedBreak = assertIs<ParameterizeBreak>(probedThrows[0])
        assertEquals("Expected to be declaring `b`, but got `a`", probedBreak.cause.message)
    }

    @Test
    fun nested_parameter_declaration_within_arguments_iterator_function() {
        val probedThrows = mutableListOf<Throwable?>()

        fun ParameterizeScope.testArguments() = object : Sequence<Unit> {
            override fun iterator(): Iterator<Unit> {
                probeThrow(probedThrows) {
                    val inner by parameterOf(Unit)
                }

                return listOf(Unit).iterator()
            }
        }

        runCatching {
            parameterize {
                val outer by parameter(testArguments())
            }
        }

        val thrownBreak = assertIs<ParameterizeBreak>(probedThrows[0])
        assertEquals(
            "Nesting parameters is not currently supported: `inner` was declared within `outer`'s arguments",
            thrownBreak.cause.message
        )
    }

    @Test
    fun nested_parameter_declaration_within_arguments_iterator_next_function() {
        val probedThrows = mutableListOf<Throwable?>()

        fun ParameterizeScope.testArgumentsIterator() = object : Iterator<Unit> {
            override fun hasNext(): Boolean = true

            override fun next() {
                probeThrow(probedThrows) {
                    val inner by parameterOf(Unit)
                }
            }
        }

        runCatching {
            parameterize {
                val outer by parameter(Sequence(::testArgumentsIterator))
            }
        }

        val probedThrow = assertIs<ParameterizeBreak>(probedThrows[0])
        assertEquals(
            "Nesting parameters is not currently supported: `inner` was declared within `outer`'s arguments",
            probedThrow.cause.message
        )
    }

    @Test
    fun nested_parameter_declaration_with_another_valid_intermediate_parameter_usage() { // TODO Remove in usage special treatment commit
        val probedThrows = mutableListOf<Throwable?>()

        runCatching {
            parameterize {
                val trackedNestingInterference by parameterOf(Unit)

                val outer by parameter {
                    with(this@parameterize) { // TODO Fix in DslMarker commit
                        probeThrow(probedThrows) {
                            val inner by parameterOf(Unit)
                        }
                    }

                    listOf(Unit)
                }
            }
        }

        val probedBreak = assertIs<ParameterizeBreak>(probedThrows[0])
        assertEquals(
            "Nesting parameters is not currently supported: `inner` was declared within `outer`'s arguments",
            probedBreak.cause.message
        )
    }

    @Test
    fun nested_parameter_declaration_with_another_valid_intermediate_parameter_usage_with_lazy_parameter_scope() { // TODO Remove in usage special treatment commit
        val probedThrows = mutableListOf<Throwable?>()

        runCatching {
            parameterize {
                val trackedNestingInterference by parameterOf(Unit)

                val outer by parameter {
                    probeThrow(probedThrows) {
                        @Suppress("DEPRECATION_ERROR")
                        val inner by parameterOf(Unit)
                    }

                    listOf(Unit)
                }

                val end by parameterOf(Unit, Unit)
            }
        }

        val probedBreak = assertIs<ParameterizeBreak>(probedThrows[0])
        assertEquals(
            "Nesting parameters is not currently supported: `inner` was declared within `outer`'s arguments",
            probedBreak.cause.message
        )
    }

    /**
     * Without [ParameterizeBreak] since this is potentially happening outside the iteration in user code.
     * This also doesn't put [parameterize] into a bad state, so there's no need to break out of the loop.
     */
    @Test
    fun declaring_parameter_after_iteration_ended() {
        var declareParameter = {}

        run exitLoop@{
            parameterize {
                declareParameter = {
                    val parameter by parameterOf(Unit)
                }

                // A non-local return ensures that all types of loop exits will be handled (with a `finally` block)
                return@exitLoop
            }
        }

        val failure = assertFailsWith<ParameterizeException> {
            declareParameter()
        }

        assertEquals("Cannot declare parameter `parameter` after its iteration has ended", failure.message)
    }

    @Test
    fun failing_earlier_than_the_previous_iteration() {
        val nondeterministicFailure = Throwable("Unexpected failure")

        val failure = assertFailsWith<ParameterizeException> {
            var shouldFail = false

            parameterize {
                if (shouldFail) throw nondeterministicFailure

                val iteration by parameter(1..2)

                shouldFail = true
            }
        }

        assertEquals(
            "Previous iteration executed to this point successfully, but now failed with the same arguments",
            failure.message,
            "message"
        )
        assertSame(nondeterministicFailure, failure.cause, "cause")
    }
}
