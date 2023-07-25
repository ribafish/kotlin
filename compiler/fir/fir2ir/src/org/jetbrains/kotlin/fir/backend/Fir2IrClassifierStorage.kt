/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.Fir2IrClassSymbol
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.IdSignature

// TODO: to remove
class Fir2IrClassifierStorage(private val components: Fir2IrComponents) : Fir2IrComponents by components {
    private val fieldsForContextReceivers: MutableMap<IrClass, List<IrField>> = mutableMapOf()

    fun getFieldsWithContextReceiversForClass(irClass: IrClass): List<IrField>? = fieldsForContextReceivers[irClass]

    fun getIrClassSymbolForNotFoundClass(classLikeLookupTag: ConeClassLikeLookupTag): IrClassSymbol {
        val classId = classLikeLookupTag.classId
        val signature = IdSignature.CommonSignature(
            packageFqName = classId.packageFqName.asString(),
            declarationFqName = classId.relativeClassName.asString(),
            id = 0,
            mask = 0,
            description = null,
        )

        val parentId = classId.outerClassId
        val parentClass = parentId?.let { getIrClassSymbolForNotFoundClass(it.toLookupTag()) }
        val irParent = parentClass?.owner!!// TODO ?: declarationStorage.getIrExternalPackageFragment(classId.packageFqName)

        val irClass = symbolTable.table.declareClassIfNotExists(signature, { Fir2IrClassSymbol(signature) }) {
            irFactory.createClass(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB,
                name = classId.shortClassName,
                visibility = DescriptorVisibilities.DEFAULT_VISIBILITY,
                symbol = it,
                kind = ClassKind.CLASS,
                modality = Modality.FINAL,
            ).apply {
                parent = irParent
            }
        }
        return irClass.symbol
    }
}
