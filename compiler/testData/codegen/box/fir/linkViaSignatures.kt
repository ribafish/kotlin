// WITH_STDLIB
// TARGET_BACKEND: JVM_IR
// LINK_VIA_SIGNATURES

fun box(): String = "OK"

interface A {
    fun foo()
}

fun test() {
    var x: Any? = null
    while (true) {
        try {
            x = ""
            require(x is A)
        } catch (e: Exception) {
            x = 1
            require(x is A)
            break
        } finally {
            x.inc() // should be error
            x.length // should be error
            x.foo() // should be ok
        }
        x.length // should be ok
        x.foo() // should be ok
    }
    x.inc() // should be ok
    x.foo() // should be ok
}
