// DUMP_SMAP
// NO_CHECK_LAMBDA_INLINING
// IGNORE_BACKEND: JVM
// IGNORE_BACKEND_MULTI_MODULE: JVM, JVM_MULTI_MODULE_OLD_AGAINST_IR
// FILE: 1.kt
package test

object A {
    inline fun test(s: () -> Unit) {
        s()
    }
}

object B {
    inline fun test2(s: () -> Unit) {
        s()
    }
}

// FILE: 2.kt
import test.*

fun <T> eval(f: () -> T) = f()

fun box(): String {
    var z = "fail"

    B.test2 {
        eval { // regenerated object in inline lambda
            A.test {
                z = "OK"
            }
        }
    }
    return z;
}

