package com.geekbrains.client;

import io.netty.channel.*;
import javafx.scene.control.Alert;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import static com.geekbrains.client.AppStarter.isNetworkServiceBusy;

@Slf4j
public class ExceptionHandler extends ChannelDuplexHandler {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        problemHandler("Connection with cloud was terminated", ctx, cause);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise.addListener(
                (future) -> {
                    if (!future.isSuccess()) {
                        problemHandler("Connection with cloud failed, server doesn't respond", ctx, future.cause());
                    }
                }
        ));
    }

    private void problemHandler(String message, ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
        String response = String.format("%s, %s.", message, cause.getMessage());
        log.info(response);
        NettyClient.controller.showAlert(Alert.AlertType.WARNING, response);
        NettyClient.controller.showLoginWindow();
        isNetworkServiceBusy = false;
    }
}
