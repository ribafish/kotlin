/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers

import org.jetbrains.kotlin.fir.analysis.cfa.AbstractFirPropertyInitializationChecker
import org.jetbrains.kotlin.fir.analysis.cfa.FirCallsEffectAnalyzer
import org.jetbrains.kotlin.fir.analysis.cfa.FirPropertyInitializationAnalyzer
import org.jetbrains.kotlin.fir.analysis.checkers.cfa.FirControlFlowChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.*
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirAnonymousFunctionParametersChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFiniteBoundRestrictionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirInlinedLambdaNonSourceAnnotationsChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirMissingDependencySupertypeChecker
import org.jetbrains.kotlin.fir.analysis.checkers.syntax.*

object CommonDeclarationCheckers : DeclarationCheckers() {
    override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker>
        get() = setOf(
            FirModifierChecker,
            FirConflictsDeclarationChecker,
            FirProjectionRelationChecker,
            FirTypeConstraintsChecker,
            FirReservedUnderscoreDeclarationChecker,
            FirUpperBoundViolatedDeclarationChecker,
            FirInfixFunctionDeclarationChecker,
            FirExposedVisibilityDeclarationChecker,
            FirCyclicTypeBoundsChecker,
            FirExpectActualDeclarationChecker,
            FirAmbiguousAnonymousTypeChecker,
            FirExplicitApiDeclarationChecker,
            FirAnnotationChecker,
            FirPublishedApiChecker,
            FirOptInMarkedDeclarationChecker,
            FirExpectConsistencyChecker,
            FirOptionalExpectationDeclarationChecker,
            FirMissingDependencySupertypeChecker.ForDeclarations,
            FirContextReceiversDeclarationChecker,
        )

    override val classLikeCheckers: Set<FirClassLikeChecker>
        get() = setOf(
            FirExpectActualClassifiersAreInBetaChecker,
        )

    override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker>
        get() = setOf(
            FirKClassWithIncorrectTypeArgumentChecker,
            FirImplicitNothingReturnTypeChecker,
            FirDynamicReceiverChecker,
        )

    override val functionCheckers: Set<FirFunctionChecker>
        get() = setOf(
            FirContractChecker,
            FirFunctionParameterChecker,
            FirFunctionReturnChecker,
            FirInlineDeclarationChecker,
            FirNonMemberFunctionsChecker,
            FirSuspendLimitationsChecker,
        )

    override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker>
        get() = setOf(
            FirFunctionNameChecker,
            FirFunctionTypeParametersSyntaxChecker,
            FirOperatorModifierChecker,
            FirTailrecFunctionChecker,
            FirMemberFunctionsChecker,
            FirDataObjectContentChecker,
            ContractSyntaxV2FunctionChecker,
            FirAnyDeprecationChecker,
        )

    override val propertyCheckers: Set<FirPropertyChecker>
        get() = setOf(
            FirInapplicableLateinitChecker,
            FirDestructuringDeclarationChecker,
            FirConstPropertyChecker,
            FirPropertyAccessorsTypesChecker,
            FirPropertyTypeParametersChecker,
            FirInitializerTypeMismatchChecker,
            FirDelegatedPropertyChecker,
            FirPropertyFieldTypeChecker,
            FirPropertyFromParameterChecker,
            FirLocalVariableTypeParametersSyntaxChecker,
            FirDelegateUsesExtensionPropertyTypeParameterChecker,
            FirLocalExtensionPropertyChecker,
            ContractSyntaxV2PropertyChecker,
            FirVolatileAnnotationChecker,
            FirInlinePropertyChecker,
        )

    override val backingFieldCheckers: Set<FirBackingFieldChecker>
        get() = setOf(
            FirExplicitBackingFieldForbiddenChecker,
            FirExplicitBackingFieldsUnsupportedChecker,
        )

    override val classCheckers: Set<FirClassChecker>
        get() = setOf(
            FirOverrideChecker,
            FirNotImplementedOverrideChecker,
            FirNotImplementedOverrideSimpleEnumEntryChecker,
            FirThrowableSubclassChecker,
            FirOpenMemberChecker,
            FirClassVarianceChecker,
            FirSealedSupertypeChecker,
            FirMemberPropertiesChecker,
            FirImplementationMismatchChecker,
            FirTypeParametersInObjectChecker,
            FirSupertypesChecker,
            FirPrimaryConstructorSuperTypeChecker,
            FirDynamicSupertypeChecker,
            FirEnumCompanionInEnumConstructorCallChecker,
            FirBadInheritedJavaSignaturesChecker,
            FirSealedInterfaceAllowedChecker,
            FirMixedFunctionalTypesInSupertypesChecker,
        )

    override val regularClassCheckers: Set<FirRegularClassChecker>
        get() = setOf(
            FirAnnotationClassDeclarationChecker,
            FirOptInAnnotationClassChecker,
            FirCommonConstructorDelegationIssuesChecker,
            FirDelegationSuperCallInEnumConstructorChecker,
            FirDelegationInExpectClassSyntaxChecker,
            FirDelegationInInterfaceSyntaxChecker,
            FirEnumClassSimpleChecker,
            FirLocalEntityNotAllowedChecker,
            FirManyCompanionObjectsChecker,
            FirMethodOfAnyImplementedInInterfaceChecker,
            FirDataClassPrimaryConstructorChecker,
            FirFunInterfaceDeclarationChecker,
            FirNestedClassChecker,
            FirValueClassDeclarationChecker,
            FirOuterClassArgumentsRequiredChecker,
            FirPropertyInitializationChecker,
            FirDelegateFieldTypeMismatchChecker,
            FirMultipleDefaultsInheritedFromSupertypesChecker,
            FirFiniteBoundRestrictionChecker,
            FirNonExpansiveInheritanceRestrictionChecker,
        )

    override val constructorCheckers: Set<FirConstructorChecker>
        get() = setOf(
            FirConstructorAllowedChecker,
        )

    override val fileCheckers: Set<FirFileChecker>
        get() = setOf(
            FirImportsChecker,
            FirOptInImportsChecker,
            FirUnresolvedInMiddleOfImportChecker,
            FirTopLevelPropertiesChecker,
        )

    override val scriptCheckers: Set<FirScriptChecker>
        get() = setOf(
            FirScriptPropertiesChecker
        )

    override val controlFlowAnalyserCheckers: Set<FirControlFlowChecker>
        get() = setOf(
            FirCallsEffectAnalyzer,
        )

    override val variableAssignmentCfaBasedCheckers: Set<AbstractFirPropertyInitializationChecker>
        get() = setOf(
            FirPropertyInitializationAnalyzer,
        )

    override val typeParameterCheckers: Set<FirTypeParameterChecker>
        get() = setOf(
            FirTypeParameterBoundsChecker,
            FirTypeParameterVarianceChecker,
            FirReifiedTypeParameterChecker,
            FirTypeParameterSyntaxChecker,
        )

    override val typeAliasCheckers: Set<FirTypeAliasChecker>
        get() = setOf(
            FirTopLevelTypeAliasChecker,
            FirActualTypeAliasChecker,
            FirActualTypealiasToSpecialAnnotationChecker,
            FirDefaultArgumentsInExpectWithActualTypealiasChecker,
            FirTypeAliasExpandsToArrayOfNothingsChecker,
        )

    override val anonymousFunctionCheckers: Set<FirAnonymousFunctionChecker>
        get() = setOf(
            FirAnonymousFunctionParametersChecker,
            FirInlinedLambdaNonSourceAnnotationsChecker,
            FirAnonymousFunctionSyntaxChecker,
        )

    override val anonymousInitializerCheckers: Set<FirAnonymousInitializerChecker>
        get() = setOf(
            FirAnonymousInitializerInInterfaceChecker
        )

    override val valueParameterCheckers: Set<FirValueParameterChecker>
        get() = setOf(
            FirValueParameterDefaultValueTypeMismatchChecker,
        )

    override val enumEntryCheckers: Set<FirEnumEntryChecker>
        get() = setOf(
            FirEnumEntriesRedeclarationChecker,
        )
}
