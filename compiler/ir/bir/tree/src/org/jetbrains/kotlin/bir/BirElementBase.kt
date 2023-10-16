/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.bir

abstract class BirElementBase : BirElement {
    internal var owner: BirForest? = null
    final override var parent: BirElement? = null
        private set

    override fun <D> acceptChildren(visitor: BirElementVisitor<D>, data: D) {}

    internal fun initChild(new: BirElement?) {
        new as BirElementBase?

        new?.checkCanBeAttachedAsChild(this)

        if (new != null) {
            new.parent = this
            childAttached(new)
        }
    }

    internal fun replaceChild(old: BirElement?, new: BirElement?) {
        old as BirElementBase?
        new as BirElementBase?

        new?.checkCanBeAttachedAsChild(this)

        if (old != null) {
            old.parent = null
            childDetached(old)
        }
        if (new != null) {
            new.parent = this
            childAttached(new)
        }
    }

    private fun childDetached(childElement: BirElementBase) {
        owner?.elementDetached(childElement)
    }

    private fun childAttached(childElement: BirElementBase) {
        owner?.elementAttached(childElement)
    }

    internal fun checkCanBeAttachedAsChild(newParent: BirElement) {
        require(parent == null) { "Cannot attach element $this as a child of $newParent as it is already a child of $parent." }
    }
}
