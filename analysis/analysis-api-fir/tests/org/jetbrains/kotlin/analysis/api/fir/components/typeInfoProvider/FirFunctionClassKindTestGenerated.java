/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components.typeInfoProvider;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.analysis.api.impl.barebone.test.FrontendApiTestConfiguratorService;
import org.jetbrains.kotlin.analysis.api.fir.FirFrontendApiTestConfiguratorService;
import org.jetbrains.kotlin.analysis.api.impl.base.test.components.typeInfoProvider.AbstractFunctionClassKindTest;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link GenerateNewCompilerTests.kt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("analysis/analysis-api/testData/components/typeInfoProvider/functionClassKind")
@TestDataPath("$PROJECT_ROOT")
public class FirFunctionClassKindTestGenerated extends AbstractFunctionClassKindTest {
    @NotNull
    @Override
    public FrontendApiTestConfiguratorService getConfigurator() {
        return FirFrontendApiTestConfiguratorService.INSTANCE;
    }

    @Test
    public void testAllFilesPresentInFunctionClassKind() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/analysis-api/testData/components/typeInfoProvider/functionClassKind"), Pattern.compile("^(.+)\\.kt$"), null, true);
    }

    @Test
    @TestMetadata("function.kt")
    public void testFunction() throws Exception {
        runTest("analysis/analysis-api/testData/components/typeInfoProvider/functionClassKind/function.kt");
    }

    @Test
    @TestMetadata("kFunction.kt")
    public void testKFunction() throws Exception {
        runTest("analysis/analysis-api/testData/components/typeInfoProvider/functionClassKind/kFunction.kt");
    }

    @Test
    @TestMetadata("kSuspendFunction.kt")
    public void testKSuspendFunction() throws Exception {
        runTest("analysis/analysis-api/testData/components/typeInfoProvider/functionClassKind/kSuspendFunction.kt");
    }

    @Test
    @TestMetadata("suspendFunction.kt")
    public void testSuspendFunction() throws Exception {
        runTest("analysis/analysis-api/testData/components/typeInfoProvider/functionClassKind/suspendFunction.kt");
    }
}
