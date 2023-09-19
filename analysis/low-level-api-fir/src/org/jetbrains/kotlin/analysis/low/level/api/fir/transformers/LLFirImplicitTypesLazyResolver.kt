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
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.isScriptDependentDeclaration
import org.jetbrains.kotlin.fir.FirElementWithResolveState
import org.jetbrains.kotlin.fir.FirFileAnnotationsContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirImplicitAwareBodyResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirResolveContextCollector
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.ImplicitBodyResolveComputationSession
import org.jetbrains.kotlin.fir.scopes.callableCopySubstitutionForTypeUpdater
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef

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
        override val buildCfgForFiles: Boolean get() = false
    }

    override fun doResolveWithoutLock(target: FirElementWithResolveState): Boolean {
        when {
            target is FirCallableDeclaration && target.attributes.callableCopySubstitutionForTypeUpdater != null -> {
                performCustomResolveUnderLock(target) {
                    transformer.returnTypeCalculator.callableCopyTypeCalculator.computeReturnType(target)
                }

                return true
            }

            target is FirFunction -> {
                resolveFunction(target)
                return true
            }

            target is FirProperty -> {
                resolveProperty(target)
                return true
            }
        }

        return super.doResolveWithoutLock(target)
    }

    private fun resolveFunction(function: FirFunction) {
        performCustomResolveUnderLock(function) {
            if (function.returnTypeRef is FirImplicitTypeRef) {
                resolve(function, BodyStateKeepers.FUNCTION)
            }
        }
    }

    private fun resolveProperty(property: FirProperty) {
        performCustomResolveUnderLock(property) {
            if (property.returnTypeRef is FirImplicitTypeRef || property.backingField?.returnTypeRef is FirImplicitTypeRef) {
                resolve(property, BodyStateKeepers.PROPERTY)
            }
        }
    }

    override fun doLazyResolveUnderLock(target: FirElementWithResolveState) {
        when (target) {
            is FirFunction, is FirProperty -> error("${target::class.simpleName} should have been resolved in ${::doResolveWithoutLock.name}")
            is FirField -> {
                if (target.returnTypeRef is FirImplicitTypeRef) {
                    resolve(target, BodyStateKeepers.FIELD)
                }
            }

            is FirScript -> {
                if (target.statements.any { it.isScriptDependentDeclaration }) {
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
        if (target is FirScript) {
            resolveScript(target)
        } else {
            super.rawResolve(target)
        }

        LLFirDeclarationModificationService.bodyResolved(target, resolverPhase)
    }
}
