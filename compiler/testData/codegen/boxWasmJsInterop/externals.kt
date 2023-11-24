// FILE: externals.js
function createObject() {
    return {
        getXmethod() { return this.x; },
        setXmethod(v) { this.x = v; }
    };
}

function setX(obj, x) {
    obj.x = x;
}

function getX(obj) {
    return obj.x;
}

const readOnlyProp = 123;
var mutableProp = "20";

class C1 {
    constructor(a, b) {
        this.a = a;
        this.b = b;
    }

    getA() { return this.a; }
    setA(v) { this.a = v; }

    getB() { return this.b; }
}

C1.Nested1 = class {}
C1.Nested1.Nested2 = class {}
C1.Nested1.Nested2.Nested3 = class {
    constructor(x) {
        this.x = x;
    }

    foo() { return this.x + " from Nested 3"; }
}

class C2 extends C1 {
    constructor(a, b) {
        super(a, b);
        this.c = "C";
    }
}


C2.Object1 = { Object2: { Object3: { x: "C2.Object1.Object2.Object3.x" } } }

const externalObj = {
    x: "externalObj.x",
    y: {  x: "externalObj.y.x" },
    c: class { x = "(new externalObj.c()).x" }
}

function jsRenamed() {
    return 'renamed'
}

function provideUShort() { return -1 }
function provideNullableUShort(nullable) { return nullable ? null : - 1 }

function consumeUShort(x) { return x.toString() }
function consumeNullableUShort(x) { return x == null ? null : x.toString() }

function provideUInt() { return -1 }
function provideNullableUInt(nullable) { return nullable ? null : - 1 }

function consumeUInt(x) { return x.toString() }
function consumeNullableUInt(x) { return x == null ? null : x.toString() }

function provideULong() { return -1n }
function provideNullableULong(nullable) { return nullable ? null : - 1n }

function consumeULong(x) { return x.toString() }
function consumeNullableULong(x) { return x == null ? null : x.toString() }

function consumeUShortVararg(x) { return x.toString() }
function consumeNullableUShortVararg(x) { return x == null ? null : x.toString() }

function consumeUIntVararg(x) { return x.toString() }
function consumeNullableUIntVararg(x) { return x == null ? null : x.toString() }

function consumeULongVararg(x) { return x.toString() }
function consumeNullableULongVararg(x) { return x == null ? null : x.toString() }

// FILE: externals.kt
external interface Obj {
    var x: Int
    fun getXmethod(): Int
    fun setXmethod(v: Int)
}
external fun createObject(): Obj
external fun setX(obj: Obj, x: Int)
external fun getX(obj: Obj): Int

external val readOnlyProp: Int
external var mutableProp: String

open external class C1 {
    constructor(a: String, b: String)
    var a: String
    val b: String

    fun getA(): String
    fun setA(x: String)
    fun getB(): String

    class Nested1 {
        class Nested2 {
            class Nested3 {
                constructor(x: String)
                fun foo(): String
            }
        }
    }
}

external class C2 : C1 {
    constructor(a: String, b: String)

    val c: String

    object Object1 {
        object Object2 {
            object Object3 {
                val x: String
            }
        }
    }
}

external object externalObj {
    val x: String
    object y {
        val x: String
    }
    class c {
        val x: String
    }
}

@JsName("jsRenamed")
external fun testJsName(): String

external fun provideUShort(): UShort

external fun provideNullableUShort(nullable: Boolean): UShort?

external fun consumeUShort(x: UShort): String

external fun consumeNullableUShort(x: UShort?): String?

external fun provideUInt(): UInt

external fun provideNullableUInt(nullable: Boolean): UInt?

external fun consumeUInt(x: UInt): String

external fun consumeNullableUInt(x: UInt?): String?

external fun provideULong(): ULong

external fun provideNullableULong(nullable: Boolean): ULong?

external fun consumeULong(x: ULong): String

external fun consumeNullableULong(x: ULong?): String?

external fun consumeUShortVararg(vararg shorts: UShort): String

external fun consumeNullableUShortVararg(vararg shorts: UShort?): String?

external fun consumeUIntVararg(vararg ints: UInt): String

external fun consumeNullableUIntVararg(vararg ints: UInt?): String?

external fun consumeULongVararg(vararg ints: ULong): String

external fun consumeNullableULongVararg(vararg ints: ULong?): String?

fun box(): String {
    val obj = createObject()
    setX(obj, 100)
    if (getX(obj) != 100) return "Fail 2"

    if (obj.x != 100) return "Fail 2.1"
    obj.x = 200
    if (getX(obj) != 200) return "Fail 2.2"
    val objXRef = obj::x
    objXRef.set(300)
    if (getX(obj) != 300 || obj.x != 300 || objXRef.get() != 300) return "Fail 2.3"

    if (obj.getXmethod() != 300) return "Fail 2.4"
    obj.setXmethod(400)
    if (obj.getXmethod() != 400 || getX(obj) != 400) return "Fail 2.5"

    if (readOnlyProp != 123) return "Fail 3"
    if (::readOnlyProp.get() != 123) return "Fail 4"
    if (mutableProp != "20") return "Fail 5"
    mutableProp = "30"
    if (mutableProp != "30") return "Fail 6"
    (::mutableProp).set("40")
    if (mutableProp != "40") return "Fail 7"

    val c1 = C1("A", "B")
    if (c1.a != "A" || c1.b != "B") return "Fail 8"
    if (c1.getA() != "A" || c1.getB() != "B") return "Fail 9"
    c1.setA("A2")
    if (c1.a != "A2") return "Fail 10"
    c1.a = "A3"
    if (c1.getA() != "A3") return "Fail 11"
    val c2 = C2("A", "B")
    if (c2.a != "A" || c2.b != "B" || c2.c != "C") return "Fail 12"
    val c2_as_c1: C1 = c2
    if (c2_as_c1.a != "A" || c2_as_c1.b != "B") return "Fail 13"

    val nested3 = C1.Nested1.Nested2.Nested3("example")
    if (nested3.foo() != "example from Nested 3") return "Fail 14"

    if (C2.Object1.Object2.Object3.x != "C2.Object1.Object2.Object3.x") return "Fail 15"
    if (externalObj.x != "externalObj.x") return "Fail 16"
    if (externalObj.y.x != "externalObj.y.x") return "Fail 17"
    if (externalObj.c().x != "(new externalObj.c()).x") return "Fail 18"


    if (c1 as Any !is C1) return "Fail 19"
    if (c2 as Any !is C1) return "Fail 20"
    if (c2 as Any !is C2) return "Fail 21"
    if (externalObj.c() as Any !is externalObj.c) return "Fail 22"
    if (10 as Any is C1) return "Fail 23"
    if (c1 as Any is C2) return "Fail 24"

    if (testJsName() != "renamed") return "Fail 25"

    if (provideUShort() != UShort.MAX_VALUE) return "Fail 26"
    if (provideNullableUShort(false) != UShort.MAX_VALUE) return "Fail 27"
    if (provideNullableUShort(true) != null) return "Fail 28"

    if (provideUInt() != UInt.MAX_VALUE) return "Fail 29"
    if (provideNullableUInt(false) != UInt.MAX_VALUE) return "Fail 30"
    if (provideNullableUInt(true) != null) return "Fail 31"

    if (provideULong() != ULong.MAX_VALUE) return "Fail 32"
    if (provideNullableULong(false) != ULong.MAX_VALUE) return "Fail 33"
    if (provideNullableULong(true) != null) return "Fail 34"

    if (consumeUShort(UShort.MAX_VALUE) != "65535") return "Fail 35"
    if (consumeNullableUShort(UShort.MAX_VALUE) != "65535") return "Fail 36"
    if (consumeNullableUShort(null) != null) return "Fail 37"

    if (consumeUInt(UInt.MAX_VALUE) != "4294967295") return "Fail 38"
    if (consumeNullableUInt(UInt.MAX_VALUE) != "4294967295") return "Fail 39"
    if (consumeNullableUInt(null) != null) return "Fail 40"

    if (consumeULong(ULong.MAX_VALUE) != "18446744073709551615") return "Fail 41"
    if (consumeNullableULong(ULong.MAX_VALUE) != "18446744073709551615") return "Fail 42"
    if (consumeNullableULong(null) != null) return "Fail 43"

    if (provideUShort() != UShort.MAX_VALUE) return "Fail 44"
    if (provideNullableUShort(false) != UShort.MAX_VALUE) return "Fail 45"
    if (provideNullableUShort(true) != null) return "Fail 46"

    if (provideUInt() != UInt.MAX_VALUE) return "Fail 47"
    if (provideNullableUInt(false) != UInt.MAX_VALUE) return "Fail 48"
    if (provideNullableUInt(true) != null) return "Fail 49"

    if (provideULong() != ULong.MAX_VALUE) return "Fail 50"
    if (provideNullableULong(false) != ULong.MAX_VALUE) return "Fail 51"
    if (provideNullableULong(true) != null) return "Fail 52"

    if (consumeUShortVararg(UShort.MAX_VALUE) != "65535") return "Fail 53"
    if (consumeNullableUShortVararg(UShort.MAX_VALUE) != "65535") return "Fail 54"
    if (consumeNullableUShortVararg(null) != null) return "Fail 55"

    if (consumeUIntVararg(UInt.MAX_VALUE) != "4294967295") return "Fail 56"
    if (consumeNullableUIntVararg(UInt.MAX_VALUE) != "4294967295") return "Fail 57"
    if (consumeNullableUIntVararg(null) != null) return "Fail 58"

    if (consumeULongVararg(ULong.MAX_VALUE) != "18446744073709551615") return "Fail 59"
    if (consumeNullableULongVararg(ULong.MAX_VALUE) != "18446744073709551615") return "Fail 60"
    if (consumeNullableULongVararg(null) != null) return "Fail 61"

    return "OK"
}
