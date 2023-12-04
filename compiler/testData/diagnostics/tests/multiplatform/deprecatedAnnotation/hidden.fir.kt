// MODULE: m1-common
// FILE: common.kt

expect fun foo(test: String)

expect class A()

expect class B() {
    val b: String
}

fun commonTest() {
    foo("common")
    A()
    B().b
}


// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

@Deprecated("", level = DeprecationLevel.HIDDEN)
actual class A

actual class B {
    @Deprecated("", level = DeprecationLevel.HIDDEN)
    actual val b: String = ""
}


@Deprecated("", level = DeprecationLevel.HIDDEN)
actual fun foo(test: String) {
}

fun main() {
    <!UNRESOLVED_REFERENCE!>foo<!>("platform")
    <!DEPRECATION_ERROR!>A<!>()
    B().<!UNRESOLVED_REFERENCE!>b<!>
}
