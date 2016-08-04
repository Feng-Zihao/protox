package org.protox.http

import io.netty.channel.ChannelFutureListener.CLOSE
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import org.protox.Config
import org.protox.gatewayTimeoutResponse
import org.protox.tryCloseChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class BackendHandler(val frontChn: SocketChannel, val proxyRule: Config.ProxyRule) : ChannelInboundHandlerAdapter() {

    val LOGGER: Logger = LoggerFactory.getLogger(BackendHandler::class.java)

    val msgHolder: Queue<HttpObject> = ArrayDeque(10)

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.channel().read()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        ReferenceCountUtil.retain(msg)
        msgHolder.add(msg as HttpObject)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {

        var msg = msgHolder.peek()
        if (msg is HttpResponse) {
            writeResponseToFrontend(ctx)
        } else {
            flushMsgHolderToBackendFromIdxThenReadOrClose(ctx)
        }
    }

    private fun writeResponseToFrontend(ctx: ChannelHandlerContext) {
        val clientResponse = msgHolder.poll() as HttpResponse
        val serverResponse = DefaultHttpResponse(
                clientResponse.protocolVersion(), clientResponse.status(), clientResponse.headers()
        )

        serverResponse.headers()[HttpHeaderNames.CONNECTION] = HttpHeaderValues.CLOSE
        if (serverResponse.headers()[HttpHeaderNames.LOCATION] != null) {
            serverResponse.headers()[HttpHeaderNames.LOCATION] = proxyRule.getReturnedLocation(
                    serverResponse.headers()[HttpHeaderNames.LOCATION]
            )
        }

        frontChn.writeAndFlush(serverResponse).addListener {
            ReferenceCountUtil.safeRelease(serverResponse)
            flushMsgHolderToBackendFromIdxThenReadOrClose(ctx)
        }

    }

    private fun flushMsgHolderToBackendFromIdxThenReadOrClose(ctx: ChannelHandlerContext) {
        if (msgHolder.isNotEmpty()) {
            val msg = msgHolder.poll()
            frontChn.writeAndFlush(msg).addListener {
                ReferenceCountUtil.safeRelease(msg)
                if (msg is LastHttpContent) {
                    ctx.channel().close()
                    frontChn.close()
                } else {
                    flushMsgHolderToBackendFromIdxThenReadOrClose(ctx)
                }
            }
        } else {
            ctx.channel().read()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        clearMsgHolder()
        super.channelInactive(ctx)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            LOGGER.debug("{}", evt.state())
            frontChn.writeAndFlush(gatewayTimeoutResponse()).addListener {
                frontChn.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(CLOSE)
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
//        super.exceptionCaught(ctx, cause)
        tryCloseChannel(ctx.channel())
        tryCloseChannel(frontChn)
    }

    private fun clearMsgHolder() {
        msgHolder.forEach {
            ReferenceCountUtil.safeRelease(it)
        }
        msgHolder.clear()
    }
}