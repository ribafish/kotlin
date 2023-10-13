// FIR_IDENTICAL
// WITH_STDLIB
// DIAGNOSTICS: -DEBUG_INFO_SMARTCAST
// DUMP_CFG

fun test1(x: Any, b: Boolean) {
    if (x !is String) {
        if (b) {
            require(x is String)
        } else {
            return
        }
    }
    x.length
}

fun test2(x: Any, b: Boolean) {
    if (x !is String) {
        if (b) {
            require(x is String) { "" }
        } else {
            return
        }
    }
    x.length
}
