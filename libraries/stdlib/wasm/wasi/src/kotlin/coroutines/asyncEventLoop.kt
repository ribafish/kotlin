/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package coroutines

import kotlin.math.min
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

@WasmImport("wasi_snapshot_preview1", "poll_oneoff")
private external fun wasiPollOneOff(subscriptionPtr: Int, eventPtr: Int, nsubscriptions: Int, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "clock_time_get")
private external fun wasiRawClockTimeGet(clockId: Int, precision: Long, resultPtr: Int): Int

private const val CLOCKID_MONOTONIC = 1

class AsyncEvent internal constructor(internal var callback: (() -> Unit)?, internal val absoluteTimeout: Long) {
    fun cancel() {
        callback = null
        nearestEventAbsoluteTime = 0
    }
}

private var inCoroutineEventLoop = false
private var currentEventsList = mutableListOf<AsyncEvent>()
private var nextEventsList = mutableListOf<AsyncEvent>()
private var nearestEventAbsoluteTime: Long = Long.MAX_VALUE
private var subscriptionPtrAddress = 0
private var eventPtrAddress = 0
private var resultPtrAddress = 0

@OptIn(UnsafeWasmMemoryApi::class)
private fun initializePointers(allocator: MemoryAllocator) {
    val subscriptionPtr = allocator.allocate(48)
    //userdata
    subscriptionPtr.storeLong(0)
    //uint8_t tag;
    (subscriptionPtr + 8).storeByte(0) //EVENTTYPE_CLOCK
    //__wasi_clockid_t id;
    (subscriptionPtr + 16).storeInt(CLOCKID_MONOTONIC) //CLOCKID_MONOTONIC
    //__wasi_timestamp_t timeout;
    //(subscriptionPtr + 24).storeLong(timeout)
    //__wasi_timestamp_t precision;
    (subscriptionPtr + 32).storeLong(0)
    //__wasi_subclockflags_t
    (subscriptionPtr + 40).storeShort(0) //ABSOLUTE_TIME=1/RELATIVE=0

    subscriptionPtrAddress = subscriptionPtr.address.toInt()

    eventPtrAddress = allocator.allocate(32).address.toInt()

    resultPtrAddress = allocator.allocate(8).address.toInt()
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun clockTimeGet(): Long = withScopedMemoryAllocator {
    val returnCode = wasiRawClockTimeGet(
        clockId = CLOCKID_MONOTONIC,
        precision = 1,
        resultPtr = resultPtrAddress
    )
    check(returnCode == 0)
    Pointer(resultPtrAddress.toUInt()).loadLong()
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun sleep(timeout: Long) {
    //__wasi_timestamp_t timeout;
    (Pointer(subscriptionPtrAddress.toUInt()) + 24).storeLong(timeout)

    val returnCode = wasiPollOneOff(
        subscriptionPtr = subscriptionPtrAddress,
        eventPtr = eventPtrAddress,
        nsubscriptions = 1,
        resultPtr = resultPtrAddress
    )

    check(returnCode == 0)
}

private fun runCoroutineEventCycle() {
    var currentAbsoluteTime = clockTimeGet()
    val sleepTimeTillNearestEvent = nearestEventAbsoluteTime - currentAbsoluteTime

    if (sleepTimeTillNearestEvent > 0) {
        sleep(sleepTimeTillNearestEvent)
        currentAbsoluteTime += sleepTimeTillNearestEvent
    }
    nearestEventAbsoluteTime = Long.MAX_VALUE

    for (currentEvent in currentEventsList) {
        val callback = currentEvent.callback ?: continue
        val eventAbsoluteTime = currentEvent.absoluteTimeout
        if (currentAbsoluteTime >= eventAbsoluteTime) {
            callback()
        } else {
            nextEventsList.add(currentEvent)
            nearestEventAbsoluteTime = min(eventAbsoluteTime, nearestEventAbsoluteTime)
        }
    }
}

fun processEventLoop() {
    check(inCoroutineEventLoop) { "Should not be called outside coroutine loop" }
    while (nextEventsList.isNotEmpty()) {
        val buffer = currentEventsList
        currentEventsList = nextEventsList
        nextEventsList = buffer
        nextEventsList.clear()
        runCoroutineEventCycle()
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
fun processCoroutineEvents() {
    if (inCoroutineEventLoop) return
    if (nextEventsList.isEmpty()) return

    try {
        inCoroutineEventLoop = true
        withScopedMemoryAllocator { allocator ->
            initializePointers(allocator)
            processEventLoop()
        }
    } finally {
        inCoroutineEventLoop = false
    }
}

fun registerCoroutineAsyncEvent(timeout: Long, callback: () -> Unit): AsyncEvent {
    require(timeout >= 0L) { "Timeout cannot be negative" }
    val absoluteCurrentTime = clockTimeGet()

    val taskAbsoluteTimePossibleOverflow = absoluteCurrentTime + timeout
    val taskAbsoluteTime = if (taskAbsoluteTimePossibleOverflow >= 0L) taskAbsoluteTimePossibleOverflow else Long.MAX_VALUE

    nearestEventAbsoluteTime = min(taskAbsoluteTime, nearestEventAbsoluteTime)
    val event = AsyncEvent(callback, taskAbsoluteTime)
    nextEventsList.add(event)
    return event
}