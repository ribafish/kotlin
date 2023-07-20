// WITH_STDLIB
// TARGET_BACKEND: JVM_IR
// LINK_VIA_SIGNATURES

// MODULE: lib

class Some {
    class Nested {
        class DeeplyNested {
            fun test(): String = "O"
        }
    }
}

// MODULE: main(lib)

fun box(): String {
    val x = Some.Nested.DeeplyNested().test()
    return x + "K"
}
