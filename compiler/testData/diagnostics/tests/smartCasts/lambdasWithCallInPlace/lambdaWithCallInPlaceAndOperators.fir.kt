// WITH_STDLIB
import kotlin.contracts.*

@Suppress(<!ERROR_SUPPRESSION!>"OPT_IN_USAGE_ERROR"<!>, "OPT_IN_USAGE_FUTURE_ERROR")
fun atLeastOnce(block: () -> Unit): Boolean {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    block()
    return true
}

@Suppress(<!ERROR_SUPPRESSION!>"OPT_IN_USAGE_ERROR"<!>, "OPT_IN_USAGE_FUTURE_ERROR")
fun exactlyOnce(block: () -> Unit): Boolean {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    block()
    return true
}

fun runWithoutContract(block: () -> Unit): Boolean {
    block()
    return true
}

fun test1(x: Any) {
    if (x !is String || atLeastOnce { x.length }) return
}

fun test2(x: Any) {
    if (x !is String || exactlyOnce { x.length }) return
}

fun test3(x: Any) {
    if (x !is String || runWithoutContract { x.length }) return
}

fun test4(x: Any) {
    if (x is String && atLeastOnce { x.length }) {
        print(x.length)
    }
}

fun test5(x: Any) {
    if (x is String && exactlyOnce { x.length }) {
        print(x.length)
    }
}

fun test6(x: Any) {
    if (x is String && runWithoutContract { x.length }) {
        print(x.length)
    }
}