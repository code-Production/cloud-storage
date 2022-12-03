package com.geekbrains.server;

import com.geekbrains.common.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.stream.ChunkedFile;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Slf4j
public class CommandHandler extends SimpleChannelInboundHandler<AbstractCommand> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractCommand msg) throws Exception {
        switch (msg.getCommand()) {
            case TRANSFER_FILE_NOTIFICATION -> {
                TransferCommand command = (TransferCommand) msg;
                command.setCommand(Commands.TRANSFER_FILE_READY);
                ctx.writeAndFlush(command);
                System.out.println(command.getFile().toString());
                NettyServer.fileReceivePipeline(command, ctx.pipeline());
//                System.out.println("TRANSFER_FILE_NOTIFICATION " + msg);
                log.debug("Got TRANSFER_FILE_NOTIFICATION command.");
            }
            case DATA_STRUCTURE_REQUEST -> {
                FileListCommand command = (FileListCommand) msg;
                command.setCommand(Commands.DATA_STRUCTURE_RESPONSE);
                NettyServer.writeFolderStructure(ctx.channel(), command);
//                System.out.println("COMMAND: " + command.getFolder().toString());
                ctx.writeAndFlush(command);
//                System.out.println("DATA_STRUCTURE_REQUEST " + msg);
                log.debug("Got DATA_STRUCTURE_REQUEST command.");
            }
            case RECEIVE_FILE_REQUEST_INFO -> {
                ReceiveCommand command = (ReceiveCommand) msg;
                command.setCommand(Commands.RECEIVE_FILE_INFO);
//                long fileSize = Files.size(serverBasePath.resolve(command.getFile().toPath()));
                long fileSize = Files.size(NettyServer.getClientFolderPath(ctx.channel()).resolve(command.getFile().toPath()));
                command.setFileSize(fileSize);
                ctx.writeAndFlush(command);
//                System.out.println("RECEIVE_FILE_REQUEST_INFO");
                log.debug("Got RECEIVE_FILE_REQUEST_INFO command.");
            }
            case RECEIVE_FILE_READY -> {
//                System.out.println("RECEIVE_FILE_READY");
                log.debug("Got RECEIVE_FILE_READY command");
                ReceiveCommand command = (ReceiveCommand) msg;
//                Path filePath = serverBasePath.resolve(command.getFile().toPath());
                Path filePath = NettyServer.getClientFolderPath(ctx.channel()).resolve(command.getFile().toPath());
                NettyServer.fileTransmitPipeline(ctx.pipeline());
                try {
                    ctx.channel().writeAndFlush(new ChunkedFile(filePath.toFile()));
                    log.info("Process of transferring the file '{}' has started.", filePath.getFileName());
                } catch (NoSuchFileException e) {
                    String response = String.format("File '%s' was not found. %s\n", filePath, e);
                    log.error(response);
                } catch (IOException e) {
                    String response = String.format("Some I/O error happened when transferring the file '%s'. %s\n", filePath, e);
                    log.error(response);
                }
                NettyServer.commandReadyPipeline(ctx.pipeline());
            }
            case RECEIVE_FILE_OK -> {
                ReceiveCommand command = (ReceiveCommand) msg;
//                System.out.println("RECEIVE_FILE_OK");
                String response = String.format(
                        "File '%s' was successfully downloaded to the client.",
                        command.getFile()
                );
                log.debug(response);
            }
            case RENAME_REQUEST -> {
//                System.out.println("RENAME_REQUEST");
                log.debug("Got RENAME_REQUEST command.");
                RenameCommand command = (RenameCommand) msg;
                command.setCommand(Commands.RENAME_RESPONSE);
                File sourceFile = new File(
                        NettyServer.getClientFolderPath(ctx.channel()).toFile(), command.getSourceFile().toString());
//                System.out.println("sourceFile " + sourceFile);
                File newFile = new File(
                        NettyServer.getClientFolderPath(ctx.channel()).toFile(), command.getNewFile().toString());
//                System.out.println("newFile " + newFile);
                String response;
                if (Files.isReadable(sourceFile.toPath())) {
                    if (sourceFile.renameTo(newFile)) {
                        response = String.format("File '%s' was successfully renamed into '%s' on cloud.\n",
                                command.getSourceFile(),
                                newFile.getName()
                        );
                        command.setSuccess(true);
                    //rename() returned false (maybe check that new filename is taken)
                    } else {
                        response = String.format("Unknown error happened when renaming file '%s' into '%s'.\n",
                                command.getSourceFile(),
                                newFile.getName()
                        );
                        command.setSuccess(false);
                    }
                //source file was not found
                } else {
                    response = String.format("File '%s' was not found on cloud.\n", command.getSourceFile());
                    command.setSuccess(false);
                }
                command.setResponse(response);
                ctx.writeAndFlush(command);
                log.debug(response.trim());
            }
            case DELETE_REQUEST -> {
                DeleteCommand command = (DeleteCommand) msg;
                command.setCommand(Commands.DELETE_RESPONSE);
                String response;
                File innerFile = command.getFile();
                try {
//                    Files.delete(serverBasePath.resolve(innerFile.toPath()));
                    Files.delete(NettyServer.getClientFolderPath(ctx.channel()).resolve(innerFile.toPath()));
                    response = String.format("File '%s' was successfully deleted from cloud.\n", innerFile);
                    command.setSuccess(true);
                } catch (NoSuchFileException e) {
                    response = String.format("File '%s' was not found on cloud.\n", innerFile);
                    command.setSuccess(false);
                } catch (DirectoryNotEmptyException e) {
                    response = String.format("You cannot delete folder '%s' if it is not empty.\n", innerFile);
                    command.setSuccess(false);
                } catch (IOException e) {
                    response = String.format("Unknown I/O error happened, %s.\n", e.getMessage());
                    command.setSuccess(false);
                }
                command.setResponse(response);
                ctx.writeAndFlush(command);
                log.debug(response.trim());
            }
            case MKDIR_REQUEST -> {
                MkdirCommand command = (MkdirCommand) msg;
                command.setCommand(Commands.MKDIR_RESPONSE);
                File folderFile = command.getFolderFile();
                String response;
                try {
//                    Path folderPath = serverBasePath.resolve(folderFile.toPath());
                    Path folderPath = NettyServer.getClientFolderPath(ctx.channel()).resolve(folderFile.toPath());
                    Files.createDirectory(folderPath);
                    command.setSuccess(true);
                    response = String.format("New folder with name '%s' was successfully created in cloud.\n", folderFile);
                } catch (IOException e) {
                    command.setSuccess(false);
                    response = String.format("Unknown I/O error happened, '%s'.\n", e);
                }
                command.setResponse(response);
                ctx.writeAndFlush(command);
                log.debug(response);
            }
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyServer.closeChannel("CommandHandler", ctx.channel());
    }

}
