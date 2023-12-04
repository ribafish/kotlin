package foo

import kotlinx.jso.JsSimpleObject

@JsSimpleObject
external interface User {
    var name: String
    val age: Int
}

fun box(): String {
    val user = User(name = "Name", age = 10)

    if (user.name != "Name") return "Fail: problem with `name` property"
    if (user.age != 10) return "Fail: problem with `age` property"

    val json = js("JSON.stringify(user)")
    if (json != "{\"age\":10,\"name\":\"Name\"}") return "Fail: got the next json: $json"

    val copy = user.copy(age = 11)

    if (copy === user) return "Fail: mutation instead of immutable copy"

    if (copy.name != "Name") return "Fail: problem with copied `name` property"
    if (copy.age != 11) return "Fail: problem with copied `age` property"

    val jsonCopy = js("JSON.stringify(copy)")
    if (jsonCopy != "{\"age\":11,\"name\":\"Name\"}") return "Fail: got the next json for the copy: $jsonCopy"

    return "OK"
}