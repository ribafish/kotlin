/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.blackbox;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.junit.jupiter.api.Tag;
import org.jetbrains.kotlin.konan.test.blackbox.support.EnforcedProperty;
import org.jetbrains.kotlin.konan.test.blackbox.support.ClassLevelProperty;
import org.jetbrains.kotlin.konan.test.blackbox.support.group.UseStandardTestCaseGroupProvider;
import org.jetbrains.kotlin.konan.test.blackbox.support.group.FirPipeline;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.GenerateNativeTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("native/native.tests/testData/standalone")
@TestDataPath("$PROJECT_ROOT")
@Tag("standalone")
@EnforcedProperty(property = ClassLevelProperty.TEST_KIND, propertyValue = "STANDALONE_NO_TR")
@UseStandardTestCaseGroupProvider()
@Tag("frontend-fir")
@FirPipeline()
public class FirNativeStandaloneTestGenerated extends AbstractNativeBlackBoxTest {
    @Test
    public void testAllFilesPresentInStandalone() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/standalone"), Pattern.compile("^(.+)\\.kt$"), null, true);
    }

    @Nested
    @TestMetadata("native/native.tests/testData/standalone/console")
    @TestDataPath("$PROJECT_ROOT")
    @Tag("standalone")
    @EnforcedProperty(property = ClassLevelProperty.TEST_KIND, propertyValue = "STANDALONE_NO_TR")
    @UseStandardTestCaseGroupProvider()
    @Tag("frontend-fir")
    @FirPipeline()
    public class Console {
        @Test
        public void testAllFilesPresentInConsole() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/standalone/console"), Pattern.compile("^(.+)\\.kt$"), null, true);
        }

        @Test
        @TestMetadata("println.kt")
        public void testPrintln() throws Exception {
            runTest("native/native.tests/testData/standalone/console/println.kt");
        }

        @Test
        @TestMetadata("readLine.kt")
        public void testReadLine() throws Exception {
            runTest("native/native.tests/testData/standalone/console/readLine.kt");
        }

        @Test
        @TestMetadata("readLineEmpty.kt")
        public void testReadLineEmpty() throws Exception {
            runTest("native/native.tests/testData/standalone/console/readLineEmpty.kt");
        }

        @Test
        @TestMetadata("readLineSingleEmptyLine.kt")
        public void testReadLineSingleEmptyLine() throws Exception {
            runTest("native/native.tests/testData/standalone/console/readLineSingleEmptyLine.kt");
        }

        @Test
        @TestMetadata("readln.kt")
        public void testReadln() throws Exception {
            runTest("native/native.tests/testData/standalone/console/readln.kt");
        }

        @Test
        @TestMetadata("readlnEmpty.kt")
        public void testReadlnEmpty() throws Exception {
            runTest("native/native.tests/testData/standalone/console/readlnEmpty.kt");
        }

        @Test
        @TestMetadata("readlnOrNullEmpty.kt")
        public void testReadlnOrNullEmpty() throws Exception {
            runTest("native/native.tests/testData/standalone/console/readlnOrNullEmpty.kt");
        }
    }

    @Nested
    @TestMetadata("native/native.tests/testData/standalone/entryPoint")
    @TestDataPath("$PROJECT_ROOT")
    @Tag("standalone")
    @EnforcedProperty(property = ClassLevelProperty.TEST_KIND, propertyValue = "STANDALONE_NO_TR")
    @UseStandardTestCaseGroupProvider()
    @Tag("frontend-fir")
    @FirPipeline()
    public class EntryPoint {
        @Test
        public void testAllFilesPresentInEntryPoint() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/standalone/entryPoint"), Pattern.compile("^(.+)\\.kt$"), null, true);
        }

        @Test
        @TestMetadata("args.kt")
        public void testArgs() throws Exception {
            runTest("native/native.tests/testData/standalone/entryPoint/args.kt");
        }

        @Test
        @TestMetadata("differentEntry.kt")
        public void testDifferentEntry() throws Exception {
            runTest("native/native.tests/testData/standalone/entryPoint/differentEntry.kt");
        }

        @Test
        @TestMetadata("differentEntryMultiModule.kt")
        public void testDifferentEntryMultiModule() throws Exception {
            runTest("native/native.tests/testData/standalone/entryPoint/differentEntryMultiModule.kt");
        }

        @Test
        @TestMetadata("differentEntryNoArgs.kt")
        public void testDifferentEntryNoArgs() throws Exception {
            runTest("native/native.tests/testData/standalone/entryPoint/differentEntryNoArgs.kt");
        }

        @Test
        @TestMetadata("mainOverloading.kt")
        public void testMainOverloading() throws Exception {
            runTest("native/native.tests/testData/standalone/entryPoint/mainOverloading.kt");
        }

        @Test
        @TestMetadata("mainOverloadingNoArgs.kt")
        public void testMainOverloadingNoArgs() throws Exception {
            runTest("native/native.tests/testData/standalone/entryPoint/mainOverloadingNoArgs.kt");
        }
    }

    @Nested
    @TestMetadata("native/native.tests/testData/standalone/termination")
    @TestDataPath("$PROJECT_ROOT")
    @Tag("standalone")
    @EnforcedProperty(property = ClassLevelProperty.TEST_KIND, propertyValue = "STANDALONE_NO_TR")
    @UseStandardTestCaseGroupProvider()
    @Tag("frontend-fir")
    @FirPipeline()
    public class Termination {
        @Test
        public void testAllFilesPresentInTermination() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("native/native.tests/testData/standalone/termination"), Pattern.compile("^(.+)\\.kt$"), null, true);
        }

        @Test
        @TestMetadata("assertFailed.kt")
        public void testAssertFailed() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/assertFailed.kt");
        }

        @Test
        @TestMetadata("assertPassed.kt")
        public void testAssertPassed() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/assertPassed.kt");
        }

        @Test
        @TestMetadata("exitProcess.kt")
        public void testExitProcess() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/exitProcess.kt");
        }

        @Test
        @TestMetadata("globalThrow.kt")
        public void testGlobalThrow() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/globalThrow.kt");
        }

        @Test
        @TestMetadata("mainThrow.kt")
        public void testMainThrow() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/mainThrow.kt");
        }

        @Test
        @TestMetadata("processUnhandledException.kt")
        public void testProcessUnhandledException() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/processUnhandledException.kt");
        }

        @Test
        @TestMetadata("terminateWithUnhandledException.kt")
        public void testTerminateWithUnhandledException() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/terminateWithUnhandledException.kt");
        }

        @Test
        @TestMetadata("unhandledExceptionHookClosure.kt")
        public void testUnhandledExceptionHookClosure() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/unhandledExceptionHookClosure.kt");
        }

        @Test
        @TestMetadata("unhandledExceptionHookFun.kt")
        public void testUnhandledExceptionHookFun() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/unhandledExceptionHookFun.kt");
        }

        @Test
        @TestMetadata("unhandledExceptionHookGet.kt")
        public void testUnhandledExceptionHookGet() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/unhandledExceptionHookGet.kt");
        }

        @Test
        @TestMetadata("unhandledExceptionHookTerminate.kt")
        public void testUnhandledExceptionHookTerminate() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/unhandledExceptionHookTerminate.kt");
        }

        @Test
        @TestMetadata("unhandledExceptionHookTerminateWithProcess.kt")
        public void testUnhandledExceptionHookTerminateWithProcess() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/unhandledExceptionHookTerminateWithProcess.kt");
        }

        @Test
        @TestMetadata("unhandledExceptionHookThrow.kt")
        public void testUnhandledExceptionHookThrow() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/unhandledExceptionHookThrow.kt");
        }

        @Test
        @TestMetadata("unhandledExceptionHookWithProcess.kt")
        public void testUnhandledExceptionHookWithProcess() throws Exception {
            runTest("native/native.tests/testData/standalone/termination/unhandledExceptionHookWithProcess.kt");
        }
    }
}
