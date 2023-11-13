/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Barriers.hpp"

#include <algorithm>
#include <atomic>

#include "GCImpl.hpp"
#include "SafePoint.hpp"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"

#if __has_feature(thread_sanitizer)
#include <sanitizer/tsan_interface.h>
#endif

using namespace kotlin;

inline constexpr auto kTagBarriers = logging::Tag::kBarriers;
#define BarriersLogDebug(active, format, ...) RuntimeLogDebug({kTagBarriers}, "%s" format, active ? "[active] " : "", ##__VA_ARGS__)

namespace {

std::atomic<bool> markBarriersEnabled = false;
std::atomic<int64_t> markingEpoch = 0;

} // namespace

void gc::barriers::BarriersThreadData::onThreadRegistration() noexcept {
    if (markBarriersEnabled.load(std::memory_order_acquire)) {
        startMarkingNewObjects(GCHandle::getByEpoch(markingEpoch.load(std::memory_order_relaxed)));
    }
}

ALWAYS_INLINE void gc::barriers::BarriersThreadData::onSafePoint() noexcept {}

void gc::barriers::BarriersThreadData::startMarkingNewObjects(gc::GCHandle gcHandle) noexcept {
    RuntimeAssert(markBarriersEnabled.load(std::memory_order_relaxed), "New allocations marking may only be requested by mark barriers");
    markHandle_ = gcHandle.mark();
}

void gc::barriers::BarriersThreadData::stopMarkingNewObjects() noexcept {
    RuntimeAssert(!markBarriersEnabled.load(std::memory_order_relaxed), "New allocations marking could only been requested by mark barriers");
    markHandle_ = std::nullopt;
}

bool gc::barriers::BarriersThreadData::shouldMarkNewObjects() const noexcept {
    return markHandle_.has_value();
}

ALWAYS_INLINE void gc::barriers::BarriersThreadData::onAllocation(ObjHeader* allocated) {
    bool shouldMark = shouldMarkNewObjects();
    RuntimeAssert(shouldMark == markBarriersEnabled.load(std::memory_order_relaxed), "New allocations marking must happen with and only with mark barriers");
    if (shouldMark) {
        auto& objectData = alloc::objectDataForObject(allocated);
        objectData.markUncontended();
        markHandle_->addObject();
    }
}

void gc::barriers::enableMarkBarriers(int64_t epoch) noexcept {
    // TODO assert STW
    auto mutators = mm::ThreadRegistry::Instance().LockForIter();
    markingEpoch.store(epoch, std::memory_order_relaxed);
    markBarriersEnabled.store(true, std::memory_order_release); // FIXME why seq_cst?
    for (auto& mutator: mutators) {
        mutator.gc().impl().gc().barriers().startMarkingNewObjects(GCHandle::getByEpoch(epoch));
    }
}

void gc::barriers::disableMarkBarriers() noexcept {
    // TODO assert STW
    auto mutators = mm::ThreadRegistry::Instance().LockForIter();
    markBarriersEnabled.store(false, std::memory_order_release); // FIXME why seq_cst?
    for (auto& mutator: mutators) {
        mutator.gc().impl().gc().barriers().stopMarkingNewObjects();
    }
}

namespace {

// TODO decide whether it's beneficial to NO_INLINE the slow path
void beforeHeapRefUpdateSlowPath(mm::DirectRefAccessor ref, ObjHeader* value) noexcept {
    auto prev = ref.loadAtomic(std::memory_order_consume);
    BarriersLogDebug(true, "Write *%p <- %p (%p overwritten)", ref.location(), value, prev);
    if (prev != nullptr) {
#if __has_feature(thread_sanitizer)
        // Tell TSAN, that we acquire here the object's memory,
        // released previously on allocation with atomic_thread_fence and __tsan_release workaround.
        __tsan_acquire(prev);
#endif
        // FIXME Redundant if the destination object is black.
        //       Yet at the moment there is now efficient way to distinguish black and gray objects.

        auto& objectData = alloc::objectDataForObject(prev);
        auto& threadData = *mm::ThreadRegistry::Instance().CurrentThreadData();
        threadData.gc().impl().gc().mark().markQueue()->tryPush(objectData);
        // No need to add the marked object in statistics here.
        // Objects will be counted on dequeue.
    }
}

} // namespace

ALWAYS_INLINE void gc::barriers::beforeHeapRefUpdate(mm::DirectRefAccessor ref, ObjHeader* value) noexcept {
    if (__builtin_expect(markBarriersEnabled.load(std::memory_order_acquire), false)) {
        beforeHeapRefUpdateSlowPath(ref, value);
    } else {
        BarriersLogDebug(false, "Write *%p <- %p (%p overwritten)", ref.location(), value, ref.load());
    }
}
