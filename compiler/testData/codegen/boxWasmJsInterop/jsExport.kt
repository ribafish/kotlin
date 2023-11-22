// TARGET_BACKEND: WASM
// MODULE: main
// FILE: externals.kt

class C(val x: Int)

@JsExport
fun makeC(x: Int): JsReference<C> = C(x).toJsReference()

@JsExport
fun getX(c: JsReference<C>): Int = c.get().x

@JsExport
fun getString(s: String): String = "Test string $s";

@JsExport
fun isEven(x: Int): Boolean = x % 2 == 0

external interface EI

@JsExport
fun eiAsAny(ei: EI): JsReference<Any> = ei.toJsReference()

@JsExport
fun anyAsEI(any: JsReference<Any>): EI = any.get() as EI

@JsExport
fun provideUShort(): UShort = UShort.MAX_VALUE

@JsExport
fun consumeUShort(x: UShort) = x.toString()

@JsExport
fun provideUInt(): UInt = UInt.MAX_VALUE

@JsExport
fun consumeUInt(x: UInt) = x.toString()

@JsExport
fun provideULong(): ULong = ULong.MAX_VALUE

@JsExport
fun consumeULong(x: ULong) = x.toString()

fun box(): String = "OK"

// FILE: entry.mjs

import main from "./index.mjs"

const c = main.makeC(300);
if (main.getX(c) !== 300) {
    throw "Fail 1";
}

if (main.getString("2") !== "Test string 2") {
    throw "Fail 2";
}

if (main.isEven(31) !== false || main.isEven(10) !== true) {
    throw "Fail 3";
}

if (main.anyAsEI(main.eiAsAny({x:10})).x !== 10) {
    throw "Fail 4";
}

if (main.provideUShort() != 65535) {
    throw "Fail 5";
}
if (main.provideUInt() != 4294967295) {
    throw "Fail 6";
}
if (main.provideULong() != 18446744073709551615n) {
    throw "Fail 7";
}

if (main.consumeUShort(-1) != "65535") {
    throw "Fail 8";
}
if (main.consumeUInt(-1) != "4294967295") {
    throw "Fail 9";
}
if (main.consumeULong(-1n) != "18446744073709551615") {
    throw "Fail 10";
}

if (main.consumeUShort(65535) != "65535") {
    throw "Fail 11";
}
if (main.consumeUInt(4294967295) != "4294967295") {
    throw "Fail 12";
}
if (main.consumeULong(18446744073709551615n) != "18446744073709551615") {
    throw "Fail 13";
}

if (main.consumeUShort(65536) != "0") {
    throw "Fail 14";
}
if (main.consumeUInt(4294967296) != "0") {
    throw "Fail 15";
}
if (main.consumeULong(18446744073709551616n) != "0") {
    throw "Fail 16";
}
