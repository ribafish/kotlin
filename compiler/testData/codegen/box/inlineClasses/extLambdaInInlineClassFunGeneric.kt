// WITH_STDLIB
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses, +GenericInlineClassParameter

fun <T> T.runExt(fn: T.() -> String) = fn()

OPTIONAL_JVM_INLINE_ANNOTATION
value class R<T: Int>(private val r: T) {
    fun test() = runExt { "OK" }
}

fun box() = R(0).test()