package org.protox

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.codec.http.*
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * Created by fengzh on 7/12/2016 AD.
 */
fun tryCloseChannel(chn: Channel?, listener: GenericFutureListener<out Future<Any>>? = null) {
    if (chn != null && chn.isActive) {
        chn.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener {
            val future = chn.close()
            if (listener != null) {
                future.addListener(listener)
            }
        }
    }
}

fun getOriginalHost(request: HttpRequest): String {
    var host = request.headers()["X-Forwarded-Host"]
    if (host.isNullOrBlank()) {
        host = request.headers()[HttpHeaderNames.HOST.toString()]
    }
    if (host.contains(":")) {
        host = host.split(":")[0]
    }
    return host
}

val URL_PATTERN = Pattern.compile("(http(s?)://)?(([0-9a-zA-Z-_]+)(\\.[0-9a-zA-Z-_]+)*)")

val WILDCARD_URL_PATTERN: Pattern = Pattern.compile("((http(s?))://)?((\\*\\.)?([0-9a-zA-Z]+\\.)*([0-9a-zA-Z]+))(:[0-9]+)?")

val frontendCounter =  AtomicInteger(0)

val BAD_GATEWAY_RESPONSE = DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.BAD_GATEWAY,
        Unpooled.copiedBuffer("Bad Gateway", Charsets.UTF_8),
        DefaultHttpHeaders().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE),
        EmptyHttpHeaders.INSTANCE)

val GATEWAY_TIMEOUT_RESPONSE = DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.GATEWAY_TIMEOUT,
        Unpooled.copiedBuffer("Gateway Timeout", Charsets.UTF_8),
        DefaultHttpHeaders().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE),
        EmptyHttpHeaders.INSTANCE)


val REQUEST_TIMEOUT_RESPONSE = DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.REQUEST_TIMEOUT,
        Unpooled.copiedBuffer("Request Timeout", Charsets.UTF_8),
        DefaultHttpHeaders().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE),
        EmptyHttpHeaders.INSTANCE)
