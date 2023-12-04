/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso

import org.jetbrains.kotlin.generators.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlinx.jso.runners.AbstractFirJsObjectIrJsBoxTest
import org.jetbrains.kotlinx.jso.runners.AbstractPsiJsObjectIrJsBoxTest

fun main(args: Array<String>) {
//    val excludedFirTestdataPattern = "^(.+)\\.fir\\.kts?\$"

    generateTestGroupSuiteWithJUnit5(args) {
        testGroup(
            "plugins/jso/tests-gen",
            "plugins/jso/testData"
        ) {
            // ------------------------------- diagnostics -------------------------------
//            testClass<AbstractJsObjectPluginDiagnosticTest>() {
//                model("diagnostics", excludedPattern = excludedFirTestdataPattern)
//            }

            // ------------------------------- box -------------------------------

            testClass<AbstractFirJsObjectIrJsBoxTest> {
                model("box")
            }
            testClass<AbstractPsiJsObjectIrJsBoxTest> {
                model("box")
            }
        }
    }
}
