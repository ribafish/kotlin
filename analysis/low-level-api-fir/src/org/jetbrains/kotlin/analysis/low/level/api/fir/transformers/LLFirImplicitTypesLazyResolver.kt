/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.transformers

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.FirDesignationWithFile
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.targets.LLFirResolveTarget
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.throwUnexpectedFirElementError
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.builder.LLFirLockProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.LLFirDeclarationModificationService
import org.jetbrains.kotlin.analysis.low.level.api.fir.lazy.resolve.FirLazyBodiesCalculator
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.blockGuard
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkReturnTypeRefIsResolved
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.expressionGuard
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.forEachDependentDeclaration
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.isScriptDependentDeclaration
import org.jetbrains.kotlin.fir.FirElementWithResolveState
import org.jetbrains.kotlin.fir.FirFileAnnotationsContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.FirFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildBackingFieldCopy
import org.jetbrains.kotlin.fir.declarations.builder.buildDefaultSetterValueParameterCopy
import org.jetbrains.kotlin.fir.declarations.builder.buildPropertyAccessorCopy
import org.jetbrains.kotlin.fir.declarations.builder.buildPropertyCopy
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunctionCopy
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameterCopy
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyBackingField
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirImplicitAwareBodyResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirResolveContextCollector
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.ImplicitBodyResolveComputationSession
import org.jetbrains.kotlin.fir.scopes.callableCopySubstitutionForTypeUpdater
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirBackingFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirDelegateFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.util.setMultimapOf
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.fir.utils.exceptions.withFirSymbolEntry
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.requireWithAttachment

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

internal class LLImplicitBodyResolveComputationSession : ImplicitBodyResolveComputationSession() {
    /**
     * The symbol on which foreign annotations will be postponed
     *
     * @see withAnchorForForeignAnnotations
     * @see postponeForeignAnnotationResolution
     */
    private var currentSymbol: FirBasedSymbol<*>? = null

    inline fun <T> withAnchorForForeignAnnotations(symbol: FirBasedSymbol<*>, action: () -> T): T {
        val oldCurrentSymbol = currentSymbol
        return try {
            currentSymbol = symbol
            action()
        } finally {
            currentSymbol = oldCurrentSymbol
        }
    }

    override fun <D : FirCallableDeclaration> executeTransformation(symbol: FirCallableSymbol<*>, transformation: () -> D): D {
        // Do not store local declarations as we can postpone only non-local callables
        return if (symbol.cannotResolveAnnotationsOnDemand()) {
            transformation()
        } else {
            withAnchorForForeignAnnotations(symbol, transformation)
        }
    }

    private val postponed = setMultimapOf<FirBasedSymbol<*>, FirBasedSymbol<*>>()

    /**
     * Postpone the resolution request to [symbol] until [annotation arguments][FirResolvePhase.ANNOTATION_ARGUMENTS] phase
     * of the declaration which is used this foreign annotation.
     *
     * @see postponedSymbols
     */
    fun postponeForeignAnnotationResolution(symbol: FirBasedSymbol<*>) {
        // We cannot resolve them on demand, so we shouldn't postpone them
        if (symbol.cannotResolveAnnotationsOnDemand()) {
            return
        }

        val currentSymbol = currentSymbol ?: errorWithAttachment("Unexpected state: the current symbol have to be here") {
            withFirSymbolEntry("symbol to postpone", symbol)
        }

        postponed.put(currentSymbol, symbol)
    }

    private fun postponedSymbolsForAnnotationResolution(element: FirElementWithResolveState): Set<FirBasedSymbol<*>> {
        if (element !is FirDeclaration) return emptySet()

        return postponed[element.symbol]
    }

    /**
     * @return all postponed symbols during [postponeForeignAnnotationResolution] for [target] element
     *
     * @see postponeForeignAnnotationResolution
     */
    fun postponedSymbols(target: FirElementWithResolveState): Collection<FirBasedSymbol<*>> {
        val result = postponedSymbolsForAnnotationResolution(target)
        if (target !is FirScript) return result

        val scriptResult = result.toMutableSet()
        target.forEachDependentDeclaration {
            scriptResult += postponedSymbolsForAnnotationResolution(it)
        }

        return scriptResult
    }
}

internal class LLFirImplicitBodyTargetResolver(
    target: LLFirResolveTarget,
    lockProvider: LLFirLockProvider,
    session: FirSession,
    scopeSession: ScopeSession,
    firResolveContextCollector: FirResolveContextCollector?,
    llImplicitBodyResolveComputationSessionParameter: LLImplicitBodyResolveComputationSession? = null,
) : LLFirAbstractBodyTargetResolver(
    target,
    lockProvider,
    scopeSession,
    FirResolvePhase.IMPLICIT_TYPES_BODY_RESOLVE,
    llImplicitBodyResolveComputationSession = llImplicitBodyResolveComputationSessionParameter ?: LLImplicitBodyResolveComputationSession(),
    isJumpingPhase = true,
) {
    private val declarationContextMapping: MutableMap<FirDeclaration, FirDeclaration> by lazy(LazyThreadSafetyMode.NONE) {
        HashMap<FirDeclaration, FirDeclaration>()
    }

    private fun <T : FirDeclaration> registerContextMapping(original: T, copy: T) {
        requireWithAttachment(original !== copy, { "Original and copied declarations are must be not the same" }) {
            withFirEntry("original", original)
        }

        declarationContextMapping[copy] = original
    }

    override val transformer = object : FirImplicitAwareBodyResolveTransformer(
        session,
        implicitBodyResolveComputationSession = llImplicitBodyResolveComputationSession,
        phase = resolverPhase,
        implicitTypeOnly = true,
        scopeSession = scopeSession,
        firResolveContextCollector = firResolveContextCollector,
        returnTypeCalculator = createReturnTypeCalculator(firResolveContextCollector = firResolveContextCollector),
    ) {

        override val preserveCFGForClasses: Boolean get() = false
        override val buildCfgForFiles: Boolean get() = false
        override fun transformForeignAnnotationCall(symbol: FirBasedSymbol<*>, annotationCall: FirAnnotationCall): FirAnnotationCall {
            llImplicitBodyResolveComputationSession.postponeForeignAnnotationResolution(symbol)
            return annotationCall
        }

        override fun <T : FirDeclaration> substituteDeclarationForContextPurposes(declaration: T): T {
            return declarationContextMapping[declaration]?.let {
                @Suppress("UNCHECKED_CAST")
                it as T
            } ?: declaration
        }
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
        onAirResolve(
            target = function,
            requireOnlyPhaseUpdate = { it.returnTypeRef !is FirImplicitTypeRef },
            copy = ::copySimpleFunction,
            preserveContext = ::preserveContextMapping,
            publishResult = ::publishFunctionResult,
        )
    }

    private fun publishFunctionResult(destination: FirFunction, source: FirFunction) {
        destination.replaceReturnTypeRef(source.returnTypeRef)
        destination.replaceControlFlowGraphReference(source.controlFlowGraphReference)
        destination.replaceBody(source.body)

        destination.valueParameters.zip(source.valueParameters).forEach { (dst, src) ->
            dst.replaceReturnTypeRef(src.returnTypeRef)
            dst.replaceDefaultValue(src.defaultValue)
            dst.replaceControlFlowGraphReference(src.controlFlowGraphReference)
        }
    }

    private fun preserveContextMapping(originalFunction: FirFunction, newFunction: FirFunction) {
        registerContextMapping(original = originalFunction, copy = newFunction)
        requireWithAttachment(
            originalFunction.valueParameters.size == newFunction.valueParameters.size,
            { "The value parameters size must be the same" },
        ) {
            withFirEntry("original", originalFunction)
            withFirEntry("copy", newFunction)
        }

        originalFunction.valueParameters.zip(newFunction.valueParameters).forEach { (original, copy) ->
            registerContextMapping(original = original, copy = copy)
        }
    }

    /**
     * @return **null** if original declaration is already resolved
     */
    private fun copySimpleFunction(originalFunction: FirFunction): FirSimpleFunction? {
        // resolveFunction can be called only for constructor, error function, and simple function,
        // but only simple function can have an implicit type
        requireWithAttachment(
            originalFunction is FirSimpleFunction,
            { "${originalFunction::class.simpleName} found but only ${FirSimpleFunction::class.simpleName} acceptable" },
        ) {
            withFirEntry("function", originalFunction)
        }

        var newFunction: FirSimpleFunction? = null
        withReadLock(originalFunction) {
            // it is enough to make a non-deep copy because we will process only default values and the body,
            // and they will be replaced later during lazy body calculation
            newFunction = buildSimpleFunctionCopy(originalFunction) {
                symbol = FirNamedFunctionSymbol(originalFunction.symbol.callableId)
                body = body?.let(::blockGuard)
                copyValueParametersFrom(originalFunction, symbol)
            }
        }

        // function already resolved
        if (newFunction == null) return null

        // insert body
        val firDesignation = FirDesignationWithFile(nestedClassesStack, originalFunction, resolveTarget.firFile)
        @Suppress("USELESS_CAST")
        FirLazyBodiesCalculator.calculateLazyBodiesForFunction(designation = firDesignation, target = newFunction as FirSimpleFunction)

        return newFunction
    }

    private fun FirFunctionBuilder.copyValueParametersFrom(originalFunction: FirFunction, newFunctionSymbol: FirFunctionSymbol<*>) {
        valueParameters.clear()
        originalFunction.valueParameters.mapTo(valueParameters) {
            buildValueParameterCopy(it) {
                symbol = FirValueParameterSymbol(it.symbol.name)
                containingFunctionSymbol = newFunctionSymbol
                defaultValue = defaultValue?.let(::expressionGuard)
            }
        }
    }

    private fun resolveProperty(property: FirProperty) {
        onAirResolve(
            target = property,
            requireOnlyPhaseUpdate = {
                it.returnTypeRef !is FirImplicitTypeRef && it.backingField?.returnTypeRef !is FirImplicitTypeRef
            },
            copy = ::copyProperty,
            preserveContext = ::preserveContextMapping,
            publishResult = ::publishPropertyResult,
        )
    }

    private fun preserveContextMapping(originalProperty: FirProperty, newProperty: FirProperty) {
        registerContextMapping(original = originalProperty, copy = newProperty)

        originalProperty.getter?.let {
            preserveContextMapping(originalFunction = it, newFunction = newProperty.getter!!)
        }

        originalProperty.setter?.let {
            preserveContextMapping(originalFunction = it, newFunction = newProperty.setter!!)
        }

        originalProperty.backingField?.let {
            registerContextMapping(original = it, copy = newProperty.backingField!!)
        }
    }

    private fun copyProperty(originalProperty: FirProperty): FirProperty? {
        var newProperty: FirProperty? = null
        withReadLock(originalProperty) {
            newProperty = buildPropertyCopy(originalProperty) {
                symbol = FirPropertySymbol(originalProperty.symbol.callableId)
                delegateFieldSymbol = delegateFieldSymbol?.let { FirDelegateFieldSymbol(it.callableId) }
                initializer = initializer?.let(::expressionGuard)
                delegate = delegate?.let(::expressionGuard)
                getter = getter?.let { copyPropertyAccessor(it, symbol) }
                setter = setter?.let { copyPropertyAccessor(it, symbol) }
                backingField = backingField?.let { copyBackingField(it, symbol) }
            }
        }

        // property already resolved
        if (newProperty == null) return null

        // insert body
        val firDesignation = FirDesignationWithFile(nestedClassesStack, originalProperty, resolveTarget.firFile)
        @Suppress("USELESS_CAST")
        FirLazyBodiesCalculator.calculateLazyBodyForProperty(designation = firDesignation, target = newProperty as FirProperty)

        return newProperty
    }

    private fun copyPropertyAccessor(
        originalAccessor: FirPropertyAccessor,
        newPropertySymbol: FirPropertySymbol,
    ): FirPropertyAccessor = when (originalAccessor) {
        is FirDefaultPropertyAccessor -> {
            FirDefaultPropertyAccessor.createGetterOrSetter(
                source = originalAccessor.source,
                origin = originalAccessor.origin,
                moduleData = originalAccessor.moduleData,
                propertyTypeRef = originalAccessor.returnTypeRef,
                visibility = originalAccessor.visibility,
                propertySymbol = newPropertySymbol,
                isGetter = originalAccessor.isGetter,
                resolvePhase = originalAccessor.resolvePhase,
            ).apply {
                replaceStatus(originalAccessor.status)
                replaceValueParameters(
                    originalAccessor.valueParameters.map {
                        buildDefaultSetterValueParameterCopy(it) {
                            symbol = FirValueParameterSymbol(it.symbol.name)
                            containingFunctionSymbol = this@apply.symbol
                            defaultValue = defaultValue?.let(::expressionGuard)
                        }
                    }
                )
            }
        }
        else -> buildPropertyAccessorCopy(originalAccessor) {
            symbol = FirPropertyAccessorSymbol()
            propertySymbol = newPropertySymbol

            body = body?.let(::blockGuard)
            copyValueParametersFrom(originalAccessor, symbol)
        }
    }

    private fun copyBackingField(originalField: FirBackingField, newPropertySymbol: FirPropertySymbol): FirBackingField {
        return if (originalField is FirDefaultPropertyBackingField) {
            FirDefaultPropertyBackingField(
                moduleData = originalField.moduleData,
                origin = originalField.origin,
                source = originalField.source,
                annotations = originalField.annotations.toMutableList(),
                returnTypeRef = originalField.returnTypeRef,
                isVar = originalField.isVar,
                propertySymbol = newPropertySymbol,
                status = originalField.status,
                resolvePhase = originalField.resolvePhase,
            )
        } else {
            buildBackingFieldCopy(originalField) {
                symbol = FirBackingFieldSymbol(originalField.symbol.callableId)
                propertySymbol = newPropertySymbol

                initializer?.let(::expressionGuard)
                delegate = delegate?.let(::expressionGuard)
            }
        }
    }

    private fun publishPropertyResult(destination: FirProperty, source: FirProperty) {
        publishVariableResult(destination, source)

        destination.getter?.let {
            val sourceGetter = source.getter!!
            publishFunctionResult(it, sourceGetter)
            FirLazyBodiesCalculator.rebindDelegatedAccessorBody(newTarget = it, oldTarget = sourceGetter)
        }

        destination.setter?.let {
            val sourceSetter = source.setter!!
            publishFunctionResult(it, sourceSetter)
            FirLazyBodiesCalculator.rebindDelegatedAccessorBody(newTarget = it, oldTarget = sourceSetter)
        }

        destination.backingField?.let {
            publishVariableResult(it, source.backingField!!)
        }

        val delegate = destination.delegate
        if (delegate is FirCall && delegate.source?.kind == KtFakeSourceElementKind.DelegatedPropertyAccessor) {
            FirLazyBodiesCalculator.rebindArgumentList(
                delegate.argumentList,
                newTarget = destination.symbol,
                oldTarget = source.symbol,
                isSetter = false,
                canHavePropertySymbolAsThisReference = false,
            )
        }

        destination.replaceBodyResolveState(source.bodyResolveState)
        destination.replaceControlFlowGraphReference(source.controlFlowGraphReference)
    }

    private fun publishVariableResult(destination: FirVariable, source: FirVariable) {
        destination.replaceReturnTypeRef(source.returnTypeRef)
        destination.replaceInitializer(source.initializer)
        destination.replaceDelegate(source.delegate)
    }

    /**
     * @param copy copy the original declaration and unwrap lazy bodies
     * @param preserveContext save declarations mapping to provide original declarations as DFA and context nodes
     */
    private inline fun <T : FirCallableDeclaration> onAirResolve(
        target: T,
        requireOnlyPhaseUpdate: (original: T) -> Boolean,
        copy: (original: T) -> T?,
        preserveContext: (original: T, copied: T) -> Unit,
        crossinline publishResult: (original: T, copied: T) -> Unit,
    ) {
        if (requireOnlyPhaseUpdate(target)) {
            return performCustomResolveUnderLock(target) {
                // just update phase
            }
        }

        // "null" means that some other thread is already resolved [target] to [resolverPhase]
        val copiedDeclaration = copy(target) ?: return

        // save declarations mapping
        preserveContext(target, copiedDeclaration)

        // we should mark the original declaration as in progress
        llImplicitBodyResolveComputationSession.compute(target.symbol) {
            // We need this lock to be sure that resolution contracts are not violated
            // It is safe because we can't get a recursion here due to a new element
            performCustomResolveUnderLock(copiedDeclaration) {
                rawResolve(copiedDeclaration)
            }

            // publish results under lock
            performCustomResolveUnderLock(target) {
                publishResult(target, copiedDeclaration)
                dumpPostponedSymbols(from = copiedDeclaration, to = target)
            }

            // store calculated result in the session
            target
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
                    /**
                     * Workaround for some generated script properties because they have <local> package,
                     * so [cannotResolveAnnotationsOnDemand] will skip them
                     */
                    llImplicitBodyResolveComputationSession.withAnchorForForeignAnnotations(target.symbol) {
                        resolve(target, BodyStateKeepers.SCRIPT)
                    }
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

        dumpPostponedSymbols(target)
    }

    private fun dumpPostponedSymbols(from: FirElementWithResolveState, to: FirElementWithResolveState = from) {
        val postponedSymbols = llImplicitBodyResolveComputationSession.postponedSymbols(from)
        if (postponedSymbols.isNotEmpty()) {
            requireWithAttachment(
                to is FirDeclaration,
                {
                    "Unexpected target: ${to::class.simpleName}\n" +
                            "We assume that only during ${FirDeclaration::class.simpleName} " +
                            "resolution it is possible to get not empty result"
                },
            ) {
                withFirEntry("target", to)
            }

            to.postponedSymbolsForAnnotationResolution = postponedSymbols
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
