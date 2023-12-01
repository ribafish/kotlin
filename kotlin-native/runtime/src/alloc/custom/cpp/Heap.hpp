/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef CUSTOM_ALLOC_CPP_HEAP_HPP_
#define CUSTOM_ALLOC_CPP_HEAP_HPP_

#include <atomic>
#include <mutex>
#include <cstring>

#include "AtomicStack.hpp"
#include "CustomAllocConstants.hpp"
#include "ExtraObjectPage.hpp"
#include "GCStatistics.hpp"
#include "Memory.h"
#include "SingleObjectPage.hpp"
#include "NextFitPage.hpp"
#include "PageStore.hpp"
#include "FixedBlockPage.hpp"
#include "CombinedFinalizerQueue.hpp"

namespace kotlin::alloc {

class Heap {
public:
    // Called once by the GC thread after all mutators have been suspended
    void PrepareForGC() noexcept;

    // Sweep through all remaining pages, freeing those blocks where CanReclaim
    // returns true. If multiple sweepers are active, each page will only be
    // seen by one sweeper.
    CombinedFinalizerQueue<FinalizerQueue> Sweep(gc::GCHandle gcHandle) noexcept;

    FixedBlockPage* GetFixedBlockPage(uint32_t cellCount, CombinedFinalizerQueue<FinalizerQueue>& finalizerQueue) noexcept;
    NextFitPage* GetNextFitPage(uint32_t cellCount, CombinedFinalizerQueue<FinalizerQueue>& finalizerQueue) noexcept;
    SingleObjectPage* GetSingleObjectPage(uint64_t cellCount, CombinedFinalizerQueue<FinalizerQueue>& finalizerQueue) noexcept;
    ExtraObjectPage* GetExtraObjectPage(CombinedFinalizerQueue<FinalizerQueue>& finalizerQueue) noexcept;

    void AddToFinalizerQueue(CombinedFinalizerQueue<FinalizerQueue> queue) noexcept;
    CombinedFinalizerQueue<FinalizerQueue> ExtractFinalizerQueue() noexcept;
    size_t EstimateOverheadPerThread() noexcept;

    // Test method
    std::vector<ObjHeader*> GetAllocatedObjects() noexcept;
    void ClearForTests() noexcept;

private:
    PageStore<FixedBlockPage> fixedBlockPages_[FIXED_BLOCK_PAGE_MAX_BLOCK_SIZE + 1];
    PageStore<NextFitPage> nextFitPages_;
    PageStore<SingleObjectPage> singleObjectPages_;
    PageStore<ExtraObjectPage> extraObjectPages_;

    CombinedFinalizerQueue<FinalizerQueue> pendingFinalizerQueue_;
    std::mutex pendingFinalizerQueueMutex_;

    std::atomic<std::size_t> concurrentSweepersCount_ = 0;
};

} // namespace kotlin::alloc

#endif
