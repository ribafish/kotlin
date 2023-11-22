// ISSUE: KT-63664
// FIR_DUMP

// MODULE: lib
// FILE: flow.kt

package flow

interface Flow<T> {
    suspend fun collect(collector: FlowCollector<T>)
}

fun interface FlowCollector<T> {
    suspend fun emit(value: T)
}

@Deprecated(level = DeprecationLevel.HIDDEN, message = "")
suspend inline fun <T> Flow<T>.collect(crossinline action: suspend (value: T) -> Unit) {}

// FILE: internal.kt

package internal

import flow.*

internal inline fun <T> unsafeFlow(crossinline block: suspend FlowCollector<T>.() -> Unit): Flow<T> {
    return object : Flow<T> {
        override suspend fun collect(collector: FlowCollector<T>) {}
    }
}

// MODULE: main(lib)
// FILE: main.kt

import flow.*
import internal.*

@Suppress("INVISIBLE_MEMBER", <!ERROR_SUPPRESSION!>"INVISIBLE_REFERENCE"<!>)
fun <T> Flow<T>.takeWhileDirect(predicate: suspend (T) -> Boolean): Flow<T> = unsafeFlow {
    collect { value ->
        if (predicate(value)) {
            emit(value)
        }
    }
}
