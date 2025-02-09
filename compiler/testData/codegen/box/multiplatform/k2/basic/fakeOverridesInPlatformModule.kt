// IGNORE_BACKEND_K1: JS, JS_IR, JS_IR_ES6, WASM
// IGNORE_NATIVE_K1: mode=ONE_STAGE_MULTI_MODULE
// !LANGUAGE: +MultiPlatformProjects

// MODULE: common
// TARGET_PLATFORM: Common
// FILE: common.kt

interface Foo {
    fun ok(): String = "OK"
}

fun test(e: Foo) = e.ok()

// MODULE: platform()()(common)
// FILE: platform.kt

interface Bar : Foo

class A : Bar

fun box() = A().ok()
