package com.cognifide.gradle.common.health

import com.cognifide.gradle.common.CommonException
import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.build.Retry
import com.cognifide.gradle.common.http.HttpClient
import com.cognifide.gradle.common.net.NetUtils
import com.cognifide.gradle.common.utils.Formats
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpRequestBase
import java.util.*
import java.util.concurrent.TimeUnit

@ExperimentalStdlibApi
class HealthChecker(val common: CommonExtension) {

    private val logger = common.logger

    private val prop = common.prop

    private val checks = mutableListOf<HealthCheck>()

    private var httpClientOptions: HttpClient.() -> Unit = {
        authorizationPreemptive.set(true)
        connectionRetries.apply {
            convention(false)
            prop.boolean("healthChecker.http.connectionRetries")?.let { set(it) }
        }
        connectionTimeout.apply {
            convention(5_000)
            prop.int("healthChecker.http.connectionTimeout")?.let { set(it) }
        }
    }

    private var httpRequestOptions: HttpRequestBase.() -> Unit = {
        prop.string("healthChecker.http.userAgent")?.let { addHeader("user-agent", it) }
    }

    val verbose = common.obj.boolean {
        convention(true)
        common.prop.boolean("healthChecker.verbose")?.let { set(it) }
    }

    var retry = common.retry {
        after(
            prop.long("healthChecker.retry.times") ?: 60,
            prop.long("healthChecker.retry.delay") ?: 5_000L,
        )
    }

    var assuranceRetry = common.retry {
        after(
            prop.long("healthChecker.assuranceRetry.times") ?: 2,
            prop.long("healthChecker.assuranceRetry.delay") ?: 1_000L,
        )
    }

    val waitBefore = common.obj.long {
        convention(TimeUnit.SECONDS.toMillis(0))
        common.prop.long("healthChecker.wait.before")?.let { set(it) }
    }

    val waitAfter = common.obj.long {
        convention(TimeUnit.SECONDS.toMillis(0))
        common.prop.long("healthChecker.wait.after")?.let { set(it) }
    }

    fun wait(before: Long, after: Long) {
        waitBefore.set(before)
        waitAfter.set(after)
    }

    fun check(name: String, check: () -> Any?) {
        checks += HealthCheck(name, check)
    }

    fun String.invoke(check: () -> Any?) = check(this, check)

    // Evaluation

    var all = listOf<HealthStatus>()

    val allStatuses get() = all.sortedWith(compareBy({ it.succeed }, { it.check.name })).joinToString("\n")

    var passed = listOf<HealthStatus>()

    val passedRatio get() = "${passed.size}/${all.size} (${Formats.percent(passed.size, all.size)})"

    var failed = listOf<HealthStatus>()

    @Suppress("ComplexMethod", "LongMethod")
    fun start(verbose: Boolean = this.verbose.get(), retry: Retry = this.retry): List<HealthStatus> {
        if (checks.isEmpty()) {
            logger.info("Health checking skipped as no checks defined.")
            return listOf()
        }

        common.progress(assuranceRetry.times) {
            step = "Health checking"

            message = "Wait Before"
            if (waitBefore.get() > 0) {
                common.progressCountdown(waitBefore.get())
            }

            var assuranceNo = 1L
            while (assuranceNo <= assuranceRetry.times) {
                var attemptSuccess = false
                var attemptExceeded = false

                for (attemptNo in 1..retry.times) {
                    message = when {
                        failed.isNotEmpty() -> "Attempt $attemptNo/${retry.times}, Check(s) succeeded ${passed.size}/${all.size}"
                        else -> "Attempt $attemptNo/${retry.times}"
                    }
                    count = assuranceNo

                    all = common.parallel.map(checks) { it.perform() }.toList()
                    passed = all.filter { it.succeed }
                    failed = all - passed.toSet()

                    if (failed.isEmpty()) {
                        attemptSuccess = true
                        break
                    }

                    assuranceNo = 1
                    if (verbose) {
                        logger.info(failed.sortedBy { it.check.name }.joinToString("\n"))
                    }

                    if (attemptNo < retry.times) {
                        Thread.sleep(retry.delay(attemptNo))
                    } else if (attemptNo == retry.times) {
                        attemptExceeded = true
                    }
                }

                if (attemptSuccess) {
                    logger.info("Health checking passed ($assuranceNo/${assuranceRetry.times})")
                    if (assuranceNo <= assuranceRetry.times) {
                        Thread.sleep(assuranceRetry.delay(assuranceNo))
                    }
                    assuranceNo++
                }
                if (attemptExceeded) {
                    val message = "Health checking failed. Success ratio: $passedRatio:\n$allStatuses"
                    when {
                        verbose -> throw HealthException(message)
                        else -> logger.error(message)
                    }
                    break
                }
            }

            message = "Wait After"
            if (waitAfter.get() > 0) {
                common.progressCountdown(waitAfter.get())
            }

            logger.lifecycle("Health checking succeed.\n$allStatuses")
        }

        return all
    }

    // Shorthand methods for defining health checks

    /**
     * Check URL using specified criteria (HTTP options and e.g text & status code assertions).
     */
    fun http(checkName: String, url: String, statusCode: Int = HttpStatus.SC_OK) {
        http(checkName, url) { respondsWith(statusCode) }
    }

    fun http(checkName: String, url: String, containedText: String, statusCode: Int = HttpStatus.SC_OK) {
        http(checkName, url) { containsText(containedText, statusCode) }
    }

    fun http(checkName: String, url: String, criteria: HttpCheck.() -> Unit) = check(checkName) {
        var result: Any? = null
        common.http {
            apply(httpClientOptions)

            val check = HttpCheck(url).apply(criteria)
            apply(check.options)

            request(check.method, check.url, httpRequestOptions) { response ->
                result = "${check.method} ${check.url} -> ${response.statusLine}"
                check.checks.forEach { it(response) }
            }
        }
        result
    }

    fun noHttp(checkName: String, url: String, criteria: HttpCheck.() -> Unit = {}) = check(checkName) {
        var result: Any? = null
        common.http {
            val check = HttpCheck(url).apply(criteria)
            apply(httpClientOptions)
            apply(check.options)
            var responds = false
            try {
                request(check.method, check.url) {
                    responds = true
                }
            } catch (e: CommonException) {
                // ignore known errors
            }
            if (responds) {
                throw IllegalStateException("HTTP ${check.method.uppercase(Locale.getDefault())} '${check.url}' is available")
            } else {
                result = "${check.method} ${check.url} -> unavailable"
            }
        }
        result
    }

    fun host(checkName: String, hostName: String, port: Int, timeout: Int = 1000) = check(checkName) {
        if (!NetUtils.isHostReachable(hostName, port, timeout)) {
            throw IllegalStateException("Host '$hostName' at port $port is not reachable")
        }
        "$hostName:$port -> reachable"
    }

    fun noHost(checkName: String, hostName: String, port: Int, timeout: Int = 1000) = check(checkName) {
        if (NetUtils.isHostReachable(hostName, port, timeout)) {
            throw IllegalStateException("Host '$hostName' at port $port is reachable")
        }
        "$hostName:$port -> unreachable"
    }

    // Default options

    fun httpClient(options: HttpClient.() -> Unit) {
        this.httpClientOptions = options
    }

    fun httpRequest(options: HttpRequestBase.() -> Unit) {
        this.httpRequestOptions = options
    }
}
