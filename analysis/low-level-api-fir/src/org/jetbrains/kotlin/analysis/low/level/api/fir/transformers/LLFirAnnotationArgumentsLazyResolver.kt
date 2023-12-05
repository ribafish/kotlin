/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.transformers

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.targets.LLFirResolveTarget
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.throwUnexpectedFirElementError
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.builder.LLFirLockProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.lazy.resolve.FirLazyBodiesCalculator
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.llFirSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkAnnotationsAreResolved
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.forEachDependentDeclaration
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.isError
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirResolveContextCollector
import org.jetbrains.kotlin.fir.resolve.transformers.plugin.FirAnnotationArgumentsTransformer
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.fir.visitors.transformSingle

internal object LLFirAnnotationArgumentsLazyResolver : LLFirLazyResolver(FirResolvePhase.ANNOTATION_ARGUMENTS) {
    override fun resolve(
        target: LLFirResolveTarget,
        lockProvider: LLFirLockProvider,
        session: FirSession,
        scopeSession: ScopeSession,
        towerDataContextCollector: FirResolveContextCollector?,
    ) {
        val resolver = LLFirAnnotationArgumentsTargetResolver(target, lockProvider, session, scopeSession, towerDataContextCollector)
        resolver.resolveDesignation()
    }

    override fun phaseSpecificCheckIsResolved(target: FirElementWithResolveState) {
        if (target !is FirAnnotationContainer) return
        checkAnnotationsAreResolved(target)

        when (target) {
            is FirCallableDeclaration -> {
                checkAnnotationsAreResolved(target, target.returnTypeRef)
                val receiverParameter = target.receiverParameter
                if (receiverParameter != null) {
                    checkAnnotationsAreResolved(receiverParameter)
                    checkAnnotationsAreResolved(target, receiverParameter.typeRef)
                }

                for (contextReceiver in target.contextReceivers) {
                    checkAnnotationsAreResolved(target, contextReceiver.typeRef)
                }
            }

            is FirTypeParameter -> {
                for (bound in target.bounds) {
                    checkAnnotationsAreResolved(target, bound)
                }
            }

            is FirClass -> {
                for (typeRef in target.superTypeRefs) {
                    checkAnnotationsAreResolved(target, typeRef)
                }
            }

            is FirTypeAlias -> {
                checkAnnotationsAreResolved(target, target.expandedTypeRef)
            }
        }
    }
}

private class LLFirAnnotationArgumentsTargetResolver(
    resolveTarget: LLFirResolveTarget,
    lockProvider: LLFirLockProvider,
    session: FirSession,
    scopeSession: ScopeSession,
    firResolveContextCollector: FirResolveContextCollector?,
) : LLFirAbstractBodyTargetResolver(
    resolveTarget,
    lockProvider,
    scopeSession,
    FirResolvePhase.ANNOTATION_ARGUMENTS,
) {
    /**
     * All foreign annotations have to be resolved before by [postponedSymbolsForAnnotationResolution] or [resolveDependencies]
     * so there is no sense to override
     * [transformForeignAnnotationCall][org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirAbstractBodyResolveTransformerDispatcher.transformForeignAnnotationCall]
     *
     * We can add additional [checkAnnotationCallIsResolved][org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkAnnotationCallIsResolved],
     * but this also doesn't make sense
     * because we anyway will check all annotations during [LLFirAnnotationArgumentsLazyResolver.phaseSpecificCheckIsResolved]
     *
     * @see postponedSymbolsForAnnotationResolution
     * @see org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirAbstractBodyResolveTransformerDispatcher.transformForeignAnnotationCall
     */
    override val transformer = FirAnnotationArgumentsTransformer(
        session,
        scopeSession,
        resolverPhase,
        returnTypeCalculator = createReturnTypeCalculator(firResolveContextCollector = firResolveContextCollector),
        firResolveContextCollector = firResolveContextCollector,
    )

    override fun doResolveWithoutLock(target: FirElementWithResolveState): Boolean {
        if (target !is FirDeclaration) return false

        var processed = false
        var symbolsToResolve: Collection<FirBasedSymbol<*>>? = null
        withReadLock(target) {
            processed = true
            symbolsToResolve = target.postponedSymbolsForAnnotationResolution
        }

        // some other thread already resolved this element to this or upper phase
        if (!processed) return true
        symbolsToResolve?.forEach { it.lazyResolveToPhase(resolverPhase) }

        return false
    }

    override fun doLazyResolveUnderLock(target: FirElementWithResolveState) {
        resolveWithKeeper(
            target,
            target.llFirSession,
            AnnotationArgumentsStateKeepers.DECLARATION,
            prepareTarget = FirLazyBodiesCalculator::calculateAnnotations,
        ) {
            transformAnnotations(target)
        }

        if (target is FirDeclaration) {
            target.postponedSymbolsForAnnotationResolution = null
        }
    }

    @Deprecated("Should never be called directly, only for override purposes, please use withRegularClass", level = DeprecationLevel.ERROR)
    override fun withRegularClassImpl(firClass: FirRegularClass, action: () -> Unit) {
        transformer.declarationsTransformer.withRegularClass(firClass) {
            action()
            firClass
        }
    }

    private fun transformAnnotations(target: FirElementWithResolveState) {
        when {
            target is FirRegularClass -> {
                val declarationTransformer = transformer.declarationsTransformer
                declarationTransformer.context.insideClassHeader(target) {
                    target.transformAnnotations(declarationTransformer, ResolutionMode.ContextIndependent)
                    target.transformTypeParameters(declarationTransformer, ResolutionMode.ContextIndependent)
                    target.transformSuperTypeRefs(declarationTransformer, ResolutionMode.ContextIndependent)
                }
            }

            target is FirScript -> {
                target.transformAnnotations(transformer.declarationsTransformer, ResolutionMode.ContextIndependent)
                transformer.declarationsTransformer.withScript(target) {
                    target.forEachDependentDeclaration {
                        it.transformSingle(transformer, ResolutionMode.ContextIndependent)
                    }

                    target
                }
            }
            target.isRegularDeclarationWithAnnotation -> target.transformSingle(transformer, ResolutionMode.ContextIndependent)
            target is FirCodeFragment || target is FirFile -> {}
            else -> throwUnexpectedFirElementError(target)
        }
    }
}

internal val FirElementWithResolveState.isRegularDeclarationWithAnnotation: Boolean
    get() = when (this) {
        is FirCallableDeclaration,
        is FirAnonymousInitializer,
        is FirDanglingModifierList,
        is FirFileAnnotationsContainer,
        is FirTypeAlias,
        -> true
        else -> false
    }

internal object AnnotationArgumentsStateKeepers {
    private val ANNOTATION: StateKeeper<FirAnnotation, FirSession> = stateKeeper { _, session ->
        add(ANNOTATION_BASE, session)
        add(FirAnnotation::argumentMapping, FirAnnotation::replaceArgumentMapping)
        add(FirAnnotation::typeArgumentsCopied, FirAnnotation::replaceTypeArguments)
    }

    private val ANNOTATION_BASE: StateKeeper<FirAnnotation, FirSession> = stateKeeper { annotation, session ->
        if (annotation is FirAnnotationCall) {
            entity(annotation, ANNOTATION_CALL, session)
        }
    }

    private val ANNOTATION_CALL: StateKeeper<FirAnnotationCall, FirSession> = stateKeeper { annotationCall, session ->
        add(FirAnnotationCall::calleeReference, FirAnnotationCall::replaceCalleeReference)

        val argumentList = annotationCall.argumentList
        if (argumentList !is FirResolvedArgumentList && argumentList !is FirEmptyArgumentList) {
            add(FirAnnotationCall::argumentList, FirAnnotationCall::replaceArgumentList) { oldList ->
                val newArguments = FirLazyBodiesCalculator.createArgumentsForAnnotation(annotationCall, session).arguments
                buildArgumentList {
                    source = oldList.source
                    for ((index, argument) in oldList.arguments.withIndex()) {
                        val replacement = when {
                            argument is FirPropertyAccessExpression && argument.calleeReference.let { it.isError() || it is FirResolvedNamedReference } -> argument
                            else -> newArguments[index]
                        }

                        arguments.add(replacement)
                    }
                }
            }
        }
    }

    val DECLARATION: StateKeeper<FirElementWithResolveState, FirSession> = stateKeeper { target, session ->
        val visitor = object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                when (element) {
                    is FirDeclaration -> if (element !== target) return // Avoid nested declarations
                    is FirAnnotation -> entity(element, ANNOTATION, session)
                    is FirStatement -> return
                }

                element.acceptChildren(this)
            }
        }

        target.accept(visitor)
    }
}

private val FirAnnotation.typeArgumentsCopied: List<FirTypeProjection>
    get() = if (typeArguments.isEmpty()) emptyList() else ArrayList(typeArguments)