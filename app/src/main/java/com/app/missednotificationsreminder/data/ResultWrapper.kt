package com.app.missednotificationsreminder.data

import com.app.missednotificationsreminder.data.ResultWrapper.Error
import com.app.missednotificationsreminder.data.ResultWrapper.Success
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.transformWhile

/**
 * A generic class that holds a value or error
 * @param <T>
 */
sealed class ResultWrapper<out R> {

    data class Success<out T>(val data: T) : ResultWrapper<T>()
    data class Error(val throwable: Throwable? = null, val code: Int = 0, val message: String? = null) :
            ResultWrapper<Nothing>() {
        fun messageOrDefault(defaultValue: () -> String): String = message ?: defaultValue()
    }

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[throwable=$throwable, code=$code, message=$message]"
        }
    }
}

/**
 * `true` if [ResultWrapper] is of type [Success] & holds non-null [Success.data].
 */
val ResultWrapper<*>.succeeded
    get() = this is Success && data != null

/**
 * Map [Result] to [ResultWrapper]
 */
fun <T> Result<T>.asResultWrapper(): ResultWrapper<T> = if (isFailure) Error(exceptionOrNull()) else Success<T>(getOrNull()!!)

/**
 * Returns the encapsulated result of the given [transform] function applied to the encapsulated value
 * if this instance represents [Success] or the
 * original encapsulated error information if it is [Error].
 *
 * Note, that this function rethrows any [Throwable] exception thrown by [transform] function.
 */
inline fun <R, T> ResultWrapper<T>.map(transform: (value: T) -> R): ResultWrapper<R> {
    return when (this@map) {
        is Success -> Success(transform(data))
        is Error -> this@map
    }
}

/**
 * Returns the encapsulated result of the given [transform] function applied to the encapsulated error information
 * if this instance represents [Error] or the
 * original encapsulated value if it is [Success].
 *
 * Note, that this function rethrows any [Throwable] exception thrown by [transform] function.
 */
inline fun <T> ResultWrapper<T>.onErrorReturn(transform: (error: Error) -> ResultWrapper<T>): ResultWrapper<T> {
    return when (this@onErrorReturn) {
        is Success -> this@onErrorReturn
        is Error -> transform(this@onErrorReturn)
    }
}

/**
 * Returns the result of the given [transform] function applied to the encapsulated value
 * if this instance represents [Success] or the
 * original encapsulated error information if it is [Error].
 *
 * Note, that this function rethrows any [Throwable] exception thrown by [transform] function.
 */
inline fun <R, T> ResultWrapper<T>.flatMap(transform: (value: T) -> ResultWrapper<R>): ResultWrapper<R> {
    return when (this@flatMap) {
        is Success -> transform(data)
        is Error -> this@flatMap
    }
}

/**
 * Performs the given [action] on the encapsulated value if this instance represents [Success].
 * Returns the original `ResultWrapper` unchanged.
 */
inline fun <T> ResultWrapper<T>.onSuccess(action: (value: T) -> Unit): ResultWrapper<T> {
    when (this) {
        is Success -> action(data)
    }
    return this
}

/**
 * Performs the given [action] on the encapsulated error information if this instance represents [Error].
 * Returns the original `Result` unchanged.
 */
inline fun <T> ResultWrapper<T>.onFailure(action: (error: Error) -> Unit): ResultWrapper<T> {
    when (this) {
        is Error -> action(this)
    }
    return this
}

/**
 * Returns the result of [onSuccess] for the encapsulated value if this instance represents [Success]
 * or the result of [onFailure] function for the encapsulated error information if it is [Error].
 *
 * Note, that this function rethrows any [Throwable] exception thrown by [onSuccess] or by [onFailure] function.
 */
inline fun <R, T> ResultWrapper<T>.fold(
        onSuccess: (value: T) -> R,
        onFailure: (error: Error) -> R
): R {
    return when (this@fold) {
        is Success -> onSuccess(data)
        is Error -> onFailure(this@fold)
    }
}

/**
 * Collect the `Flow` results until last or [Error] value is emitted. Collect
 * data using [collector].
 *
 * Note, that this function rethrows any [Throwable] exception thrown by [collector] function.
 */
suspend fun <T, R> Flow<ResultWrapper<T>>.collectWithLastErrorOrSuccessStatusSimple(
        defaultValue: ResultWrapper<R>,
        collector: (R, T) -> R): ResultWrapper<R> {
    return collectWithLastErrorOrSuccessStatus(
            defaultValue,
            { it.succeeded })
    { mergedValue, value ->
        value.map {
            val mergedData = (mergedValue as Success<R>).data
            collector(mergedData, it)
        }
    }
}

/**
 * Collect the `Flow` results until last or not succeeded value is emitted. Collect
 * data using [collector].
 *
 * Note, that this function rethrows any [Throwable] exception thrown by [collector] function.
 */
suspend fun <T, R> Flow<T>.collectWithLastErrorOrSuccessStatus(
        defaultValue: R,
        succeededTest: (T) -> Boolean,
        collector: suspend (R, T) -> R): R {
    return transformWhile {
        emit(it)
        succeededTest(it)
    }
            .fold(defaultValue, collector)
}
