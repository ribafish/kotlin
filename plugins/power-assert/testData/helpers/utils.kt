fun verifyMessage(expected: String, block: () -> Unit): String {
    try {
        block()
        return "Fail: no failure"
    } catch (e: Throwable) {
        return if (expected == e.message) "OK"
        else "FAIL\nexpected:\n${expected}\nactual:\n${e.stackTraceToString()}"
    }
}
