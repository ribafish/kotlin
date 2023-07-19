// WITH_STDLIB
// TARGET_BACKEND: JVM_IR
// LINK_VIA_SIGNATURES

// MODULE: lib

class Some {
    class Nested {
        class DeeplyNested {
            fun test(): String = "OK"
        }
    }
}

// MODULE: main(lib)

fun box(): String = Some.Nested.DeeplyNested().test()
