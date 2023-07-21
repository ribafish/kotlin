// KJS_WITH_FULL_RUNTIME
class Some {
    val x
        get() = "OK"
}
fun box() : String {
    return Some().x
}
