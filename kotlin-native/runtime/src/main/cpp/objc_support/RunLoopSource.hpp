/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#if KONAN_HAS_FOUNDATION_FRAMEWORK

#include <CoreFoundation/CoreFoundation.h>
#include <CoreFoundation/CFRunLoop.h>

#include "RawPtr.hpp"
#include "Utils.hpp"

namespace kotlin::objc_support {

class RunLoopSource : private Pinned {
public:
    class [[nodiscard]] Subscription : private Pinned {
    public:
        ~Subscription() {
            RuntimeAssert(runLoop_ == CFRunLoopGetCurrent(), "Must be destroyed on the same thread as created");
            CFRunLoopRemoveSource(runLoop_, source_, mode_);
        }

    private:
        friend class RunLoopSource;

        Subscription(CFRunLoopSourceRef source, CFRunLoopMode mode) noexcept :
            source_(source), runLoop_(CFRunLoopGetCurrent()), mode_(mode) {
            CFRunLoopAddSource(runLoop_, source, mode);
        }

        CFRunLoopSourceRef source_;
        CFRunLoopRef runLoop_;
        CFRunLoopMode mode_;
    };

    explicit RunLoopSource(std::function<void()> callback) noexcept :
        callback_(std::move(callback)),
        sourceContext_{0, &callback_, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, &perform},
        source_(CFRunLoopSourceCreate(nullptr, 0, &sourceContext_)) {}

    ~RunLoopSource() { CFRelease(source_); }

    CFRunLoopSourceRef handle() noexcept { return source_; }

    void signal() noexcept { CFRunLoopSourceSignal(source_); }

    Subscription attachToCurrentRunLoop(CFRunLoopMode mode = kCFRunLoopDefaultMode) noexcept {
        return Subscription(source_, mode);
    }

private:
    static void perform(void* callback) noexcept { static_cast<decltype(callback_)*>(callback)->operator()(); }

    std::function<void()> callback_; // TODO: std::function_ref?
    CFRunLoopSourceContext sourceContext_;
    CFRunLoopSourceRef source_;
};

} // namespace kotlin::objc_support

#endif