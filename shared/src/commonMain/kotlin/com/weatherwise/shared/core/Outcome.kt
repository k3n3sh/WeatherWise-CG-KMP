package com.weatherwise.shared.core

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/** Every network call ends in one of these: the value, or the reason it failed. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

sealed interface AppError {
    data object Network : AppError
    data object Timeout : AppError
    data class Server(val code: Int) : AppError
    data object Parsing : AppError
    data object Unknown : AppError
}

/** Thrown when a response parses but doesn't make sense, e.g. arrays of different lengths. */
class MalformedResponseException(message: String) : Exception(message)

/** The only place exceptions are turned into an AppError. Nothing above the data layer throws. */
suspend fun <T> safeCall(block: suspend () -> T): Outcome<T> {
    try {
        return Outcome.Success(block())
    } catch (e: CancellationException) {
        // The user left the screen. Swallowing this would keep cancelled work alive.
        throw e
    } catch (e: HttpRequestTimeoutException) {
        return Outcome.Failure(AppError.Timeout)
    } catch (e: ConnectTimeoutException) {
        return Outcome.Failure(AppError.Timeout)
    } catch (e: SocketTimeoutException) {
        return Outcome.Failure(AppError.Timeout)
    } catch (e: ResponseException) {
        return Outcome.Failure(AppError.Server(e.response.status.value))
    } catch (e: ContentConvertException) {
        return Outcome.Failure(AppError.Parsing)
    } catch (e: SerializationException) {
        return Outcome.Failure(AppError.Parsing)
    } catch (e: MalformedResponseException) {
        return Outcome.Failure(AppError.Parsing)
    } catch (e: IOException) {
        return Outcome.Failure(AppError.Network)
    } catch (e: Exception) {
        return Outcome.Failure(AppError.Unknown)
    }
}
