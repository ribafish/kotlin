/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#if KONAN_HAS_FOUNDATION_FRAMEWORK

#include <chrono>
#include <CoreFoundation/CoreFoundation.h>
#include <CoreFoundation/CFRunLoop.h>

#include "RawPtr.hpp"
#include "Utils.hpp"

namespace kotlin::objc_support {

class RunLoopTimer : private Pinned {
public:
    class [[nodiscard]] Subscription : private Pinned {
    public:
        ~Subscription() {
            RuntimeAssert(runLoop_ == CFRunLoopGetCurrent(), "Must be destroyed on the same thread as created");
            CFRunLoopRemoveTimer(runLoop_, timer_, mode_);
        }

    private:
        friend class RunLoopTimer;

        Subscription(CFRunLoopTimerRef timer, CFRunLoopMode mode) noexcept :
            timer_(timer), runLoop_(CFRunLoopGetCurrent()), mode_(mode) {
            CFRunLoopAddTimer(runLoop_, timer, mode);
        }

        CFRunLoopTimerRef timer_;
        CFRunLoopRef runLoop_;
        CFRunLoopMode mode_;
    };

    RunLoopTimer(
            std::function<void()> callback,
            std::chrono::duration<double> interval,
            std::chrono::duration<double> initialFiring) noexcept :
        callback_(std::move(callback)),
        timerContext_{0, &callback_, nullptr, nullptr, nullptr},
        timer_(CFRunLoopTimerCreate(
                nullptr,
                CFAbsoluteTimeGetCurrent() + initialFiring.count(),
                interval.count(),
                0,
                0,
                &perform,
                &timerContext_)) {}

    ~RunLoopTimer() { CFRelease(timer_); }

    CFRunLoopTimerRef handle() noexcept { return timer_; }

    Subscription attachToCurrentRunLoop(CFRunLoopMode mode = kCFRunLoopDefaultMode) noexcept {
        return Subscription(timer_, mode);
    }

    void setNextFiring(std::chrono::duration<double> interval) noexcept {
        CFRunLoopTimerSetNextFireDate(timer_, CFAbsoluteTimeGetCurrent() + interval.count());
    }

private:
    static void perform(CFRunLoopTimerRef, void* callback) noexcept { static_cast<decltype(callback_)*>(callback)->operator()(); }

    std::function<void()> callback_; // TODO: std::function_ref?
    CFRunLoopTimerContext timerContext_;
    CFRunLoopTimerRef timer_;
};

} // namespace kotlin::objc_support

#endif