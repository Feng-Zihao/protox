package org.protox

import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler

var backendEventLoopGroup = NioEventLoopGroup()


fun main(args: Array<String>) {

    val bossGroup = NioEventLoopGroup(1)
    val workerGroup = NioEventLoopGroup()

    var serverBootstrap = ServerBootstrap()


    try {
        serverBootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childOption(ChannelOption.AUTO_READ, false)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_REUSEADDR, true)
                .childHandler(object : ChannelInitializer<SocketChannel> () {
                    override fun initChannel(ch: SocketChannel?) {
                        ch!!.pipeline().addLast(HttpRequestDecoder())
                        ch.pipeline().addLast(HttpResponseEncoder())
//                        ch.pipeline().addLast(FrontendHandler())
                    }
                })
                .bind(8080).sync().channel().closeFuture().sync()

    } finally {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }


    println("Hello, World")
}
