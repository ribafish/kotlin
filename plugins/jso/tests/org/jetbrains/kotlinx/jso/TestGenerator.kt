/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso

import org.jetbrains.kotlin.generators.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlinx.jso.runners.*

fun main(args: Array<String>) {
    val excludedFirTestdataPattern = "^(.+)\\.fir\\.kts?\$"

    generateTestGroupSuiteWithJUnit5(args) {
        testGroup(
            "plugins/jso/tests-gen",
            "plugins/jso/testData"
        ) {
            // ------------------------------- diagnostics -------------------------------
            testClass<AbstractJsObjectPluginDiagnosticTest>() {
                model("diagnostics", excludedPattern = excludedFirTestdataPattern)
            }

//            testClass<AbstractJsObjectFirDiagnosticTest> {
//                model("diagnostics", excludedPattern = excludedFirTestdataPattern)
//                model("firMembers")
//            }

            // ------------------------------- box -------------------------------

            testClass<AbstractJsObjectIrJsBoxTest> {
                model("box")
            }
        }
    }
}
