package org.protox.http

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import org.protox.tryCloseChannel

class BackendHandler(val frontChn: SocketChannel) : SimpleChannelInboundHandler<HttpObject>(false) {

    var serverResponse: HttpResponse? = null
    var clientResponse: HttpResponse? = null
    var consumeFirstContent: Boolean = false

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.read()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        ReferenceCountUtil.retain(msg)
        if (msg is HttpResponse) {
            clientResponse = msg
            serverResponse = DefaultHttpResponse(
                    msg.protocolVersion(), msg.status(), msg.headers()
            )
            serverResponse!!.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        } else if (msg is HttpContent) {
            if (!consumeFirstContent) {
                frontChn.writeAndFlush(serverResponse).addListener {
                    if (it.isSuccess) {
                        consumeFirstContent = true
                        flushAndTryReadNextOrClose(ctx, msg)
                    } else {
                        tryCloseChannel(frontChn)
                        tryCloseChannel(ctx.channel())
                    }
                }
            } else {
                flushAndTryReadNextOrClose(ctx, msg)
            }

        }

    }

    private fun flushAndTryReadNextOrClose(ctx: ChannelHandlerContext, msg: HttpContent) {
        frontChn.writeAndFlush(msg).addListener {
            ReferenceCountUtil.release(msg)
            if (it.isSuccess) {
                if (msg !is LastHttpContent) {
                    ctx.read()
                } else {
                    tryCloseChannel(ctx.channel())
                    tryCloseChannel(frontChn)
                }
            } else {
                tryCloseChannel(ctx.channel())
                tryCloseChannel(frontChn)
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        tryCloseChannel(ctx.channel())
        tryCloseChannel(frontChn)
//        super.exceptionCaught(ctx, cause)

    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        if (clientResponse != null) {
            ReferenceCountUtil.release(clientResponse)
        }
    }
}