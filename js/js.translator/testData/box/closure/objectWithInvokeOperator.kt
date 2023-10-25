// EXPECTED_REACHABLE_NODES: 1310
package foo

interface Foo {
    companion object {
        operator fun invoke() = "OK"
    }
}
fun box(): String {
    return Foo()
}