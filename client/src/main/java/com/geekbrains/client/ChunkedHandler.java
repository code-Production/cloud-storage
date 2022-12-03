package com.geekbrains.client;


import com.geekbrains.common.Commands;
import com.geekbrains.common.ReceiveCommand;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.geekbrains.client.AppStarter.clientBasePath;
import static com.geekbrains.client.AppStarter.isNetworkServiceBusy;

@Slf4j
public class ChunkedHandler extends ChannelInboundHandlerAdapter {

    private RandomAccessFile ras;
    private FileChannel fileChannel;
    private ReceiveCommand command;
    private Path filePath;
    private long fileSize;

//    private Path serverBasePath = Paths.get("src/main/java/com/geekbrains/netty/server/files");

    public ChunkedHandler(ReceiveCommand command) {
        this.command = command;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        fileSize = command.getFileSize();
//        System.out.println("filesize: " + fileSize);
        filePath = clientBasePath.resolve(command.getFile().getName());
//        System.out.println("filePath: " + filePath);
        //catch IOEx
        //TODO check this out

        if (ras == null || fileChannel == null) {
            log.debug("Process of receiving the file '{}' has started.", filePath.getFileName());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        }


        ByteBuf buf = (ByteBuf) msg;
        ByteBuffer buffer = buf.nioBuffer();

        if (ras == null) {
//            System.out.println("ras+");
            ras = new RandomAccessFile(filePath.toString(), "rw");
        }
        if (fileChannel == null) {
//            System.out.println("fCh+");
            fileChannel = ras.getChannel();
        }

        while (buffer.hasRemaining()) {
            fileChannel.position(Files.size(filePath));
            fileChannel.write(buffer);
        }

        buf.release();
//        log.info("GOT PART OF FILE: " + Files.size(filePath) + " OUT OF " + fileSize);

        if (Files.size(filePath) == fileSize) {
//            System.out.println("GOT FULL FILE");
            closeResources();
            command.setCommand(Commands.RECEIVE_FILE_OK);
            ctx.writeAndFlush(command);
            NettyClient.commandReadyPipeline(ctx.pipeline());
            String response = String.format("File '%s' was successfully received from cloud.\n", command.getFile().getName());
            Platform.runLater(() -> {
                NettyClient.controller.updateClientFilesList();
                NettyClient.controller.consoleLog.appendText(response);
            });
            log.info(response.trim());
            isNetworkServiceBusy = false;
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyClient.closeChannel(ctx.channel());
        closeResources();
    }

    private void closeResources() throws IOException {
        if (ras != null) {
            ras.close();
        }
        if (fileChannel != null) {
            fileChannel.close();
        }
        log.debug("File resources closed.");
    }
}

