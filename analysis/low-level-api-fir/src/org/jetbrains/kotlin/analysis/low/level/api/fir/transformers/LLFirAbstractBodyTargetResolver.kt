/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.transformers

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.FirDesignationWithFile
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.targets.LLFirResolveTarget
import org.jetbrains.kotlin.analysis.low.level.api.fir.element.builder.LLFirReturnTypeCalculatorWithJump
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.builder.LLFirLockProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.lazy.resolve.FirLazyBodiesCalculator
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.isElementWhichShouldBeResolvedAsPartOfScript
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirElementWithResolveState
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.ReturnTypeCalculator
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirAbstractBodyResolveTransformerDispatcher
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirResolveContextCollector
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.ImplicitBodyResolveComputationSession
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.transformSingle

internal abstract class LLFirAbstractBodyTargetResolver(
    resolveTarget: LLFirResolveTarget,
    lockProvider: LLFirLockProvider,
    private val scopeSession: ScopeSession,
    resolvePhase: FirResolvePhase,
    protected val implicitBodyResolveComputationSession: ImplicitBodyResolveComputationSession = ImplicitBodyResolveComputationSession(),
    isJumpingPhase: Boolean = false,
) : LLFirTargetResolver(resolveTarget, lockProvider, resolvePhase, isJumpingPhase) {
    protected fun createReturnTypeCalculator(
        firResolveContextCollector: FirResolveContextCollector?,
    ): ReturnTypeCalculator = LLFirReturnTypeCalculatorWithJump(
        scopeSession,
        implicitBodyResolveComputationSession,
        lockProvider,
        firResolveContextCollector,
    )

    abstract val transformer: FirAbstractBodyResolveTransformerDispatcher

    override fun checkResolveConsistency() {
        check(resolverPhase == transformer.transformerPhase) {
            "Inconsistent Resolver($resolverPhase) and Transformer(${transformer.transformerPhase}) phases"
        }
    }

    override fun withScript(firScript: FirScript, action: () -> Unit) {
        transformer.declarationsTransformer?.withScript(firScript) {
            action()
            firScript
        }
    }

    override fun withFile(firFile: FirFile, action: () -> Unit) {
        transformer.declarationsTransformer?.withFile(firFile) {
            action()
            firFile
        }
    }

    @Deprecated("Should never be called directly, only for override purposes, please use withRegularClass", level = DeprecationLevel.ERROR)
    override fun withRegularClassImpl(firClass: FirRegularClass, action: () -> Unit) {
        transformer.declarationsTransformer?.context?.withContainingClass(firClass) {
            transformer.declarationsTransformer?.withRegularClass(firClass) {
                action()
                firClass
            }
        }
    }

    protected fun <T : FirElementWithResolveState> resolve(target: T, keeper: StateKeeper<T, FirDesignationWithFile>) {
        val firDesignation = FirDesignationWithFile(nestedClassesStack, target, resolveTarget.firFile)
        resolveWithKeeper(target, firDesignation, keeper, { FirLazyBodiesCalculator.calculateBodies(firDesignation) }) {
            rawResolve(target)
        }
    }

    protected open fun rawResolve(target: FirElementWithResolveState) {
        target.transformSingle(transformer, ResolutionMode.ContextIndependent)
    }

    protected fun resolveScript(script: FirScript) {
        transformer.declarationsTransformer?.withScript(script) {
            script.parameters.forEach { it.transformSingle(transformer, ResolutionMode.ContextIndependent) }
            script.transformDeclarations(
                transformer = object : FirTransformer<Any?>() {
                    override fun <E : FirElement> transformElement(element: E, data: Any?): E {
                        if (element !is FirDeclaration || !element.isElementWhichShouldBeResolvedAsPartOfScript) return element

                        transformer.firResolveContextCollector?.addDeclarationContext(element, transformer.context)
                        return element.transformSingle(transformer, ResolutionMode.ContextIndependent)
                    }
                },
                data = null,
            )

            script
        }
    }
}
