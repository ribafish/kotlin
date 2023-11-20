/*
* Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
* that can be found in the LICENSE file.
*/

#pragma once

#if KONAN_HAS_FOUNDATION_FRAMEWORK

#include <atomic>
#include <CoreFoundation/CFRunLoop.h>

#include "ScopedThread.hpp"
#include "Utils.hpp"

namespace kotlin::objc_support::test_support {

class RunLoopInScopedThread : private Pinned {
public:
    template <typename Init>
    explicit RunLoopInScopedThread(Init init) noexcept :
        thread_([&]() noexcept {
            [[maybe_unused]] auto state = init();
            runLoop_.store(CFRunLoopGetCurrent(), std::memory_order_release);
            CFRunLoopRun();
        }) {
        while (runLoop_.load(std::memory_order_acquire) == nullptr) {
        }
    }

    ~RunLoopInScopedThread() {
        CFRunLoopStop(runLoop_.load(std::memory_order_relaxed));
        thread_.join();
    }

    CFRunLoopRef handle() noexcept { return runLoop_.load(std::memory_order_relaxed); }

    void wakeUp() noexcept { CFRunLoopWakeUp(runLoop_.load(std::memory_order_relaxed)); }

private:
    std::atomic<CFRunLoopRef> runLoop_ = nullptr;
    ScopedThread thread_;
};

}

#endif