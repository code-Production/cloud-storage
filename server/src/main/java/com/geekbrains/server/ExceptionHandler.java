package com.geekbrains.server;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class ExceptionHandler extends ChannelDuplexHandler {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("ExceptionHandler: exceptionCaught='{}'", cause.getMessage());
        NettyServer.closeChannel("ExceptionHandler", ctx.channel());
    }

}
