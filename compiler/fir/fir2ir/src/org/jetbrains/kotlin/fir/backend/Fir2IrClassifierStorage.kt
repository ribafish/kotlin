/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField

// TODO: to remove
class Fir2IrClassifierStorage(private val components: Fir2IrComponents) : Fir2IrComponents by components {
    private val fieldsForContextReceivers: MutableMap<IrClass, List<IrField>> = mutableMapOf()

    fun getFieldsWithContextReceiversForClass(irClass: IrClass): List<IrField>? = fieldsForContextReceivers[irClass]
}
