/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.transformers

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.targets.LLFirResolveTarget
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.throwUnexpectedFirElementError
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.builder.LLFirLockProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.LLFirDeclarationModificationService
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkReturnTypeRefIsResolved
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.forEachDependentDeclaration
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.isScriptDependentDeclaration
import org.jetbrains.kotlin.fir.FirElementWithResolveState
import org.jetbrains.kotlin.fir.FirFileAnnotationsContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirImplicitAwareBodyResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirResolveContextCollector
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.ImplicitBodyResolveComputationSession
import org.jetbrains.kotlin.fir.scopes.callableCopySubstitutionForTypeUpdater
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.visitors.transformSingle

internal object LLFirImplicitTypesLazyResolver : LLFirLazyResolver(FirResolvePhase.IMPLICIT_TYPES_BODY_RESOLVE) {
    override fun resolve(
        target: LLFirResolveTarget,
        lockProvider: LLFirLockProvider,
        session: FirSession,
        scopeSession: ScopeSession,
        towerDataContextCollector: FirResolveContextCollector?,
    ) {
        val resolver = LLFirImplicitBodyTargetResolver(target, lockProvider, session, scopeSession, towerDataContextCollector)
        resolver.resolveDesignation()
    }

    override fun phaseSpecificCheckIsResolved(target: FirElementWithResolveState) {
        if (target !is FirCallableDeclaration) return
        checkReturnTypeRefIsResolved(target)
    }
}

internal class LLFirImplicitBodyTargetResolver(
    target: LLFirResolveTarget,
    lockProvider: LLFirLockProvider,
    session: FirSession,
    scopeSession: ScopeSession,
    firResolveContextCollector: FirResolveContextCollector?,
    implicitBodyResolveComputationSession: ImplicitBodyResolveComputationSession? = null,
) : LLFirAbstractBodyTargetResolver(
    target,
    lockProvider,
    scopeSession,
    FirResolvePhase.IMPLICIT_TYPES_BODY_RESOLVE,
    implicitBodyResolveComputationSession = implicitBodyResolveComputationSession ?: ImplicitBodyResolveComputationSession(),
    isJumpingPhase = true,
) {
    override val transformer = object : FirImplicitAwareBodyResolveTransformer(
        session,
        implicitBodyResolveComputationSession = this.implicitBodyResolveComputationSession,
        phase = resolverPhase,
        implicitTypeOnly = true,
        scopeSession = scopeSession,
        firResolveContextCollector = firResolveContextCollector,
        returnTypeCalculator = createReturnTypeCalculator(firResolveContextCollector = firResolveContextCollector),
    ) {
        override val preserveCFGForClasses: Boolean get() = false
        override val buildCfgForScripts: Boolean get() = false
        override val buildCfgForFiles: Boolean get() = false
    }

    override fun doLazyResolveUnderLock(target: FirElementWithResolveState) {
        when (target) {
            is FirFunction -> {
                if (target.returnTypeRef is FirImplicitTypeRef) {
                    resolve(target, BodyStateKeepers.FUNCTION)
                }
            }

            is FirProperty -> {
                if (target.returnTypeRef is FirImplicitTypeRef || target.backingField?.returnTypeRef is FirImplicitTypeRef) {
                    resolve(target, BodyStateKeepers.PROPERTY)
                }
            }

            is FirField -> {
                if (target.returnTypeRef is FirImplicitTypeRef) {
                    resolve(target, BodyStateKeepers.FIELD)
                }
            }

            is FirScript -> {
                if (target.declarations.any { it.isScriptDependentDeclaration }) {
                    resolve(target, BodyStateKeepers.SCRIPT)
                }
            }

            is FirRegularClass,
            is FirTypeAlias,
            is FirFile,
            is FirCodeFragment,
            is FirAnonymousInitializer,
            is FirDanglingModifierList,
            is FirFileAnnotationsContainer,
            is FirEnumEntry,
            is FirErrorProperty,
            -> {
                // No implicit bodies here
            }
            else -> throwUnexpectedFirElementError(target)
        }
    }

    override fun rawResolve(target: FirElementWithResolveState) {
        when {
            target is FirScript -> {
                transformer.declarationsTransformer.withScript(target) {
                    target.forEachDependentDeclaration {
                        it.transformSingle(transformer, ResolutionMode.ContextIndependent)
                    }

                    target
                }
            }

            target is FirCallableDeclaration && target.attributes.callableCopySubstitutionForTypeUpdater != null -> {
                transformer.returnTypeCalculator.callableCopyTypeCalculator.computeReturnType(target)
                Unit
            }

            else -> super.rawResolve(target)
        }

        LLFirDeclarationModificationService.bodyResolved(target, resolverPhase)
    }
}
