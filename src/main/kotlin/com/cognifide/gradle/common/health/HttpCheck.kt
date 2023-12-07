package com.cognifide.gradle.common.health

import com.cognifide.gradle.common.http.HttpClient
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus

@ExperimentalStdlibApi
class HttpCheck(val url: String) {

    var method = "GET"

    internal var options: HttpClient.() -> Unit = {}

    fun options(options: HttpClient.() -> Unit) {
        this.options = options
    }

    internal val checks = mutableListOf<HttpClient.(HttpResponse) -> Unit>()

    fun check(spec: HttpClient.(HttpResponse) -> Unit) {
        checks.add(spec)
    }

    // Shorthand methods for checking responses

    fun respondsWith(vararg statusCodes: Int) = respondsWith(statusCodes.asIterable())

    fun respondsWith(statusCodes: Iterable<Int>) {
        check { response ->
            checkStatus(response, statusCodes.toList())
        }
    }

    fun respondsOk() = respondsWith(HttpStatus.SC_OK)

    fun containsText(text: String, statusCode: Int = HttpStatus.SC_OK) = containsTexts(listOf(text), statusCode)

    fun containsTexts(vararg texts: String, statusCode: Int = HttpStatus.SC_OK) = containsTexts(texts.asIterable(), statusCode)

    fun containsTexts(texts: Iterable<String>, statusCode: Int = HttpStatus.SC_OK) {
        check { response ->
            checkStatus(response, statusCode)
            checkTexts(response, texts)
        }
    }
}
