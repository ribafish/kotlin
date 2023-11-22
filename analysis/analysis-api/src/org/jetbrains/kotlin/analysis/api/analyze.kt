/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(KtAnalysisApiInternals::class)

package org.jetbrains.kotlin.analysis.api

import org.jetbrains.kotlin.analysis.api.session.KtAnalysisSessionProvider
import org.jetbrains.kotlin.analysis.project.structure.DanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtModuleStructureInternals
import org.jetbrains.kotlin.analysis.project.structure.isDangling
import org.jetbrains.kotlin.analysis.project.structure.withDanglingFileResolutionMode
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * Executes the given [action] in a [KtAnalysisSession] context.
 *
 * The project will be analyzed from the perspective of [useSiteKtElement]'s module, also called the use-site module.
 *
 * @see KtAnalysisSession
 */
public inline fun <R> analyze(
    useSiteKtElement: KtElement,
    action: KtAnalysisSession.() -> R
): R =
    KtAnalysisSessionProvider.getInstance(useSiteKtElement.project)
        .analyse(useSiteKtElement, action)

/**
 * Executes the given [action] in a [KtAnalysisSession] context.
 *
 * The project will be analyzed from the perspective of the given [useSiteKtModule].
 *
 * @see KtAnalysisSession
 * @see KtLifetimeTokenFactory
 */
public inline fun <R> analyze(
    useSiteKtModule: KtModule,
    crossinline action: KtAnalysisSession.() -> R
): R {
    val sessionProvider = KtAnalysisSessionProvider.getInstance(useSiteKtModule.project)
    return sessionProvider.analyze(useSiteKtModule, action)
}

/**
 * Executes the given [action] in the context of a [KtAnalysisSession] that depends on an original analysis session determined by
 * [originalFile]. In addition to the symbols provided by the original analysis session, the dependent analysis session provides its own
 * symbols derived from analyzing [elementToReanalyze]. This allows analyzing some new or copied (and modified) element in the larger
 * context of the original analysis session.
 *
 * @see KtAnalysisSession.createContextDependentCopy
 */
public inline fun <R> analyzeInDependedAnalysisSession(
    originalFile: KtFile,
    elementToReanalyze: KtElement,
    action: KtAnalysisSession.() -> R
): R =
    KtAnalysisSessionProvider.getInstance(originalFile.project)
        .analyseInDependedAnalysisSession(originalFile, elementToReanalyze, action)

/**
 * Executes the given [action] in a [KtAnalysisSession] context, assuming that the [useSiteKtElement] is inside an in-memory file copy.
 * Depending on the passed [resolutionMode], declarations inside a file copy will be treated in a specific way.
 *
 * The project will be analyzed from the perspective of [useSiteKtElement]'s module, also called the use-site module.
 */
@OptIn(KtModuleStructureInternals::class)
public inline fun <R> analyzeCopy(
    useSiteKtElement: KtElement,
    resolutionMode: DanglingFileResolutionMode,
    crossinline action: KtAnalysisSession.() -> R,
): R {
    val containingFile = useSiteKtElement.containingKtFile
    require(containingFile.isDangling) { "analyzeCopy() is only available to dangling files" }

    return withDanglingFileResolutionMode(containingFile, resolutionMode) {
        analyze(containingFile, action)
    }
}