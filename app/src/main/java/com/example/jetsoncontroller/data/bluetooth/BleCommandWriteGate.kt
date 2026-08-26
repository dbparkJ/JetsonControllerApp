package com.example.jetsoncontroller.data.bluetooth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Android only reports that a GATT write was queued from writeCharacteristic().
 * This gate keeps COMMAND writes single-flight and completes them from the actual
 * onCharacteristicWrite callback.
 */
internal class BleCommandWriteGate(
    private val timeoutMillis: Long
) {
    private val writeMutex = Mutex()
    private val pendingLock = Any()
    private var pending: CompletableDeferred<Result<Unit>>? = null

    suspend fun write(
        initiate: () -> Boolean,
        abortInFlight: (String) -> Unit
    ): Result<Unit> = writeMutex.withLock {
        val completion = CompletableDeferred<Result<Unit>>()
        synchronized(pendingLock) {
            check(pending == null) { "A BLE command write is already pending" }
            pending = completion
        }

        var initiated = false
        try {
            initiated = initiate()
            if (!initiated) {
                return@withLock Result.failure(
                    IllegalStateException("BLE 명령 쓰기 요청을 GATT 큐에 등록하지 못했습니다.")
                )
            }
            withTimeout(timeoutMillis) {
                completion.await()
            }
        } catch (error: TimeoutCancellationException) {
            if (initiated) {
                abortInFlight("BLE 명령 쓰기 응답 시간이 초과되었습니다.")
            }
            Result.failure(
                IllegalStateException("BLE 명령 쓰기 응답 시간이 초과되었습니다.", error)
            )
        } catch (error: CancellationException) {
            if (initiated) {
                abortInFlight("BLE 명령 쓰기가 취소되었습니다.")
            }
            throw error
        } finally {
            synchronized(pendingLock) {
                if (pending === completion) {
                    pending = null
                }
            }
        }
    }

    fun complete(result: Result<Unit>) {
        synchronized(pendingLock) {
            pending
        }?.complete(result)
    }

    fun fail(error: Throwable) {
        complete(Result.failure(error))
    }
}
