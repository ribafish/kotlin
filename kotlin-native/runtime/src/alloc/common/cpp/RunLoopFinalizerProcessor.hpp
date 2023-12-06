/*
* Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
* that can be found in the LICENSE file.
*/

#pragma once



#include <mutex>

#include "Clock.hpp"
#include "Utils.hpp"
#include "objc_support/RunLoopSource.hpp"
#include "objc_support/RunLoopTimer.hpp"
#include "objc_support/AutoreleasePool.hpp"

namespace kotlin::alloc {

struct RunLoopFinalizerProcessorConfig {
    // This is best-effort max time. If some finalizer takes too long, we will wait for the entire finalizer to finish, but
    // won't start the next one.
    std::chrono::nanoseconds maxTimeInTask = std::chrono::milliseconds(5);
    std::chrono::nanoseconds minTimeBetweenTasks = std::chrono::milliseconds(10);
};

#if KONAN_HAS_FOUNDATION_FRAMEWORK
template <typename FinalizerQueue, typename FinalizerQueueTraits>
class RunLoopFinalizerProcessor : private Pinned {
public:
    class [[nodiscard]] Subscription : private Pinned {
    public:
        ~Subscription() = default;

    private:
        friend class RunLoopFinalizerProcessor;

        explicit Subscription(RunLoopFinalizerProcessor& owner) noexcept :
            sourceSubscription_(owner.source_.attachToCurrentRunLoop()),
            timerSubscription_(owner.timer_.attachToCurrentRunLoop()) {}

        objc_support::RunLoopSource::Subscription sourceSubscription_;
        objc_support::RunLoopTimer::Subscription timerSubscription_;
    };

    RunLoopFinalizerProcessor() noexcept = default;

    void schedule(FinalizerQueue tasks) noexcept {
        if (FinalizerQueueTraits::isEmpty(tasks))
            return;
        {
            std::unique_lock guard(queueMutex_);
            FinalizerQueueTraits::add(queue_, std::move(tasks));
        }
        source_.signal();
    }

    template <typename F>
    std::invoke_result_t<F, RunLoopFinalizerProcessorConfig&> withConfig(F&& f) noexcept {
        std::unique_lock guard(configMutex_);
        return std::invoke(std::forward<F>(f), config_);
    }

    Subscription attachToCurrentRunLoop() noexcept { return Subscription(*this); }

private:
    void process() noexcept {
        auto startTime = steady_clock::now();
        {
            std::unique_lock guard(configMutex_);
            auto minStartTime = lastProcessTimestamp_ + config_.minTimeBetweenTasks;
            if (startTime < minStartTime) {
                auto interval = minStartTime - startTime;
                // TODO: std::common_type between `saturating<…>` and `double` failed. Figure out how to fix properly.
                using UnsaturatedDuration = std::chrono::duration<decltype(interval)::rep::value_type, decltype(interval)::period>;
                // `process` is being called too frequently. Wait until the next allowed time.
                timer_.setNextFiring(std::chrono::duration_cast<UnsaturatedDuration>(interval));
                return;
            }
        }
        auto deadline = [&]() noexcept {
            std::unique_lock guard(configMutex_);
            RuntimeLogDebug({ kTagGC }, "Processing finalizers on a run loop for maximum %" PRId64 "ms",
                            std::chrono::duration_cast<std::chrono::milliseconds>(config_.maxTimeInTask).count());
            return startTime + config_.maxTimeInTask;
        }();
        while (true) {
            auto now = steady_clock::now();
            if (now > deadline) {
                // Finalization is being run too long. Stop processing and reschedule until the next allowed time.
                std::unique_lock guard(configMutex_);
                RuntimeLogDebug({ kTagGC }, "Processing finalizers on a run loop has taken %" PRId64" ms. Stopping for %" PRId64 "ms.",
                                std::chrono::duration_cast<milliseconds>(now - startTime).count().value,
                                std::chrono::duration_cast<std::chrono::milliseconds>(config_.minTimeBetweenTasks).count());
                timer_.setNextFiring(config_.minTimeBetweenTasks);
                lastProcessTimestamp_ = now;
                return;
            }
            {
                objc_support::AutoreleasePool autoreleasePool;
                if (FinalizerQueueTraits::processSingle(currentQueue_)) {
                    continue;
                }
            }
            // Attempt to fill `currentQueue_` from the global `queue_`.
            std::unique_lock guard(queueMutex_);
            FinalizerQueueTraits::add(currentQueue_, std::move(queue_));
            queue_ = FinalizerQueue{};
            if (FinalizerQueueTraits::isEmpty(currentQueue_)) {
                // If `currentQueue_` is still empty, we're done with all the queued finalizers.
                // Also, let's keep this under the lock. This way if someone were to schedule new tasks, they
                // would definitely have to wait long enough to see the updated lastProcessTimestamp_.
                lastProcessTimestamp_ = steady_clock::now();
                RuntimeLogDebug({ kTagGC }, "Processing finalizers on a run loop has finished in %" PRId64 "ms.",
                                std::chrono::duration_cast<milliseconds>(lastProcessTimestamp_ - startTime).count().value);
                return;
            }
        }
    }

    std::mutex configMutex_;
    RunLoopFinalizerProcessorConfig config_;

    std::mutex queueMutex_;
    FinalizerQueue queue_;
    FinalizerQueue currentQueue_;

    steady_clock::time_point lastProcessTimestamp_ = steady_clock::time_point::min(); // Only accessed by the process() function called only by the `CFRunLoop`.

    objc_support::RunLoopSource source_{[this]() noexcept { process(); }};
    objc_support::RunLoopTimer timer_{[this]() noexcept { source_.signal(); }, std::chrono::hours(100), std::chrono::hours(100) };
};
#endif

}