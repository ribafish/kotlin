// !LANGUAGE: +MultiPlatformProjects
// !OPT_IN: kotlin.ExperimentalMultiplatform
// TARGET_BACKEND: JS_IR
// TARGET_BACKEND: NATIVE
// TARGET_BACKEND: WASM
// TARGET_BACKEND: JVM_IR

// IGNORE_BACKEND_K1: JVM, JVM_IR, JS, JS_IR, JS_IR_ES6, WASM
// IGNORE_NATIVE_K1: mode=ONE_STAGE_MULTI_MODULE
// WITH_STDLIB

// MODULE: common
// FILE: common.kt

expect class Foo {
    val p: Int
    fun bar(r: () -> Int = this::p): Int
}

// MODULE: actual()()(common)
// FILE: actual.kt

actual class Foo {
    actual val p = 42
    actual fun bar(r: () -> Int) = r()
}
fun box(): String {
    val bar = Foo().bar()
    if (bar != 42)
        return "bar is wrongly $bar"

    return "OK"
}
