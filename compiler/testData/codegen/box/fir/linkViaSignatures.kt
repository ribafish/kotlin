// WITH_STDLIB
// TARGET_BACKEND: JVM_IR
// LINK_VIA_SIGNATURES

// MODULE: lib

fun box(): String {
    val x = listOf("OK")
    return x.first()
}
