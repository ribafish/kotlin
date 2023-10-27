// TARGET_BACKEND: NATIVE
// FILECHECK_STAGE: RemoveRedundantSafepoints

// This test is useless in debug mode.
// TODO(KT-59288): add ability to ignore tests in debug mode

// This test might fail under -Xbinary=gc=stwms and -Xbinary=gc=noop. In this case, just add ignore clause.

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
import kotlin.native.Retain

class C

fun f(): Any {
    return C()
}

fun g() = f()

// CHECK-LABEL: define %struct.ObjHeader* @"kfun:#h(kotlin.Boolean){}kotlin.Any"
@Retain
fun h(cond: Boolean): Any {
    // CHECK-OPT: _ZN12_GLOBAL__N_115safePointActionE
    // CHECK-WATCHOS_ARM32-OPT: {{call .*Kotlin_mm_safePointFunctionPrologue}}
    // CHECK-OPT-NOT: _ZN12_GLOBAL__N_115safePointActionE
    // CHECK-OPT-NOT: {{call .*Kotlin_mm_safePointFunctionPrologue}}
    // CHECK: br
    if (cond) {
        // CHECK-OPT-NOT: _ZN12_GLOBAL__N_115safePointActionE
        // CHECK-OPT-NOT: {{call .*Kotlin_mm_safePointFunctionPrologue}}
        // CHECK: br
        return listOf(C(), C())
    } else {
        // CHECK-OPT-NOT: _ZN12_GLOBAL__N_115safePointActionE
        // CHECK-OPT-NOT: {{call .*Kotlin_mm_safePointFunctionPrologue}}
        return listOf(C(), C(), C())
    }
// CHECK-LABEL: epilogue
}

// CHECK-LABEL: define %struct.ObjHeader* @"kfun:#box(){}kotlin.String"
@Retain
fun box(): String {
    // CHECK-OPT: _ZN12_GLOBAL__N_115safePointActionE
    // CHECK-WATCHOS_ARM32-OPT: {{call .*Kotlin_mm_safePointFunctionPrologue}}
    // CHECK-OPT-NOT: _ZN12_GLOBAL__N_115safePointActionE
    // CHECK-OPT-NOT: {{call .*Kotlin_mm_safePointFunctionPrologue}}
    println(g())
    println(h(true))
    return "OK"
// CHECK-LABEL: epilogue
}
