private interface A
private class B : A

private fun test1(x:Any?) {
    if (x is A || x !is B) {
        var k: B? = <!TYPE_MISMATCH!>x<!>
        var k2: A? = <!TYPE_MISMATCH!>x<!>
        var k3: Any? = x
    }
    else {
        var k: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k2: A = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k3: B = <!DEBUG_INFO_SMARTCAST!>x<!>
    }
}

private fun test2(x:Any?) {
    if (x is A || x is B) {
        var k: B = <!TYPE_MISMATCH!>x<!>
        var k2: A = <!TYPE_MISMATCH!>x<!>
        var k3: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
    }
    else {
        var k: Any? = x
        var k2: A = <!TYPE_MISMATCH!>x<!>
        var k3: B = <!TYPE_MISMATCH!>x<!>
    }
}

private fun test3(x:Any?) {
    if (x !is A || x is B) {
        var k: B = <!TYPE_MISMATCH!>x<!>
        var k2: A = <!TYPE_MISMATCH!>x<!>
        var k3: Any? = x
    }
    else {
        var k: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k2: A = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k3: B = <!TYPE_MISMATCH!>x<!>
    }
}

private fun test4(x:Any?) {
    if (x is A || x is B || x == null) {
        var k: B? = <!TYPE_MISMATCH!>x<!>
        var k2: A? = <!TYPE_MISMATCH!>x<!>
        var k3: Any? = x
    }
    else {
        var k: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k2: A = <!TYPE_MISMATCH!>x<!>
        var k3: B = <!TYPE_MISMATCH!>x<!>
    }
}

private sealed interface A2
private class B2 : A2
private class C2 : A2

private fun test5(x:Any?) {
    if (x is C2 || x is B2) {
        var k: B2 = <!TYPE_MISMATCH!>x<!>
        var k2: A2 = <!TYPE_MISMATCH!>x<!>
        var k3: C2 = <!TYPE_MISMATCH!>x<!>
        var k4: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
    }
}

private fun test6(x:Any?) {
    if (x is C2 || x !is B2) {
        var k: B2 = <!TYPE_MISMATCH!>x<!>
        var k2: A2 = <!TYPE_MISMATCH!>x<!>
        var k3: C2 = <!TYPE_MISMATCH!>x<!>
        var k4: Any? = x
    } else {
        var k: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k2: A2 = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k3: B2 = <!DEBUG_INFO_SMARTCAST!>x<!>
    }
}

private fun test7(x:Any?) {
    if (x is A2 || x !is B2) {
        var k: B2 = <!TYPE_MISMATCH!>x<!>
        var k2: A2 = <!TYPE_MISMATCH!>x<!>
        var k3: C2 = <!TYPE_MISMATCH!>x<!>
        var k4: Any? = x
    }
    else {
        var k: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k2: A2 = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k3: B2 = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k4 : C2 = <!TYPE_MISMATCH!>x<!>
    }
}

private fun test8(x:Any?) {
    if (x is A2 || x is B2) {
        var k: B2 = <!TYPE_MISMATCH!>x<!>
        var k2: A2 = <!TYPE_MISMATCH!>x<!>
        var k3: C2 = <!TYPE_MISMATCH!>x<!>
        var k4: Any? = x
    }
    else {
        var k: Any? = x
        var k2: A2 = <!TYPE_MISMATCH!>x<!>
        var k3: B2 = <!TYPE_MISMATCH!>x<!>
        var k4 : C2 = <!TYPE_MISMATCH!>x<!>
    }
}

private fun test9(x:Any?) {
    if (x !is A2 || x is B2) {
        var k: B2 = <!TYPE_MISMATCH!>x<!>
        var k2: A2 = <!TYPE_MISMATCH!>x<!>
        var k3: C2 = <!TYPE_MISMATCH!>x<!>
        var k4: Any? = x
    }
    else {
        var k: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k2: A2 = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k3: B2 = <!TYPE_MISMATCH!>x<!>
        var k4 : C2 = <!TYPE_MISMATCH!>x<!>
    }
}

private fun test10(x:Any?) {
    if (x !is A2 || x is B2) {
        var k: B2 = <!TYPE_MISMATCH!>x<!>
        var k2: A2 = <!TYPE_MISMATCH!>x<!>
        var k3: C2 = <!TYPE_MISMATCH!>x<!>
        var k4: Any? = x
    }
    else {
        var k: Any = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k2: A2 = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k3: B2 = <!TYPE_MISMATCH!>x<!>
        var k4 : C2 = <!TYPE_MISMATCH!>x<!>
    }
}

private fun test11(x:A2?) {
    if (x !is B2 || <!USELESS_IS_CHECK!>x is B2<!>) {
        var k: A2? = x
    }
    else {
        var k : B2 = <!DEBUG_INFO_SMARTCAST!>x<!>
        var k2 : A2? = x
    }
}