package com.geekbrains.client;

import com.geekbrains.common.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static com.geekbrains.client.AppStarter.clientBasePath;
import static com.geekbrains.client.AppStarter.isNetworkServiceBusy;


@Slf4j
public class NettyClient {

    public static final String HOST_NAME = "localhost";
    public static final int PORT = 8189;

    private static boolean isNetworkServiceWorking;

    private static SocketChannel channel;

    protected static Controller controller;

    private static Thread mainThread;

    public static ChannelFuture future;


    public static void start(Controller control, Runnable runnable) {
        log.info("Network service started.");
        controller = control;
        mainThread = new Thread(() -> {
            EventLoopGroup main = new NioEventLoopGroup();
            try {

                Bootstrap bootstrap = new Bootstrap();
                future = bootstrap.group(main)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                channel = ch;
                                commandReadyPipeline(channel.pipeline());
                            }

                        }).connect(HOST_NAME, PORT).sync();

                future.addListener( future -> {
                    //delayed code
                    isNetworkServiceWorking = true;
                    if (runnable != null) {
                        runnable.run();
                    }
                });


                String message = String.format("Client successfully connected to the server at '%s':'%s'\n", HOST_NAME, PORT);
                controller.consoleLog.appendText(message);
                log.info(message.trim());
                future.channel().closeFuture().sync(); //block here

            } catch (InterruptedException e) {
                log.debug("mainThread was interrupted, message={}.", e.getMessage());
            } finally {
                //todo
                main.shutdownGracefully();
                isNetworkServiceWorking = false;
                log.info("Network service stopped.");
            }
        });
        mainThread.start();
    }


    protected static void commandReadyPipeline(ChannelPipeline pipeline) {

        cleanPipeline(pipeline);
        pipeline.addLast("##commandInput", new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
        pipeline.addLast("##commandOutput", new ObjectEncoder());
        pipeline.addLast("##commandHandler", new CommandHandler());
        pipeline.addLast("##exceptionHandler", new ExceptionHandler());

    }

    protected static void fileTransmitPipeline(ChannelPipeline pipeline) {

        cleanPipeline(pipeline);
        pipeline.addLast("##commandInput", new ObjectDecoder(ClassResolvers.cacheDisabled(null)));//response
        pipeline.addLast("##fileOutput", new ChunkedWriteHandler());
        pipeline.addLast("##exceptionHandler", new ExceptionHandler());

    }

    protected static void fileReceivePipeline(ReceiveCommand command, ChannelPipeline pipeline) {

        cleanPipeline(pipeline);
        pipeline.addLast("##commandOutput", new ObjectEncoder()); //response
        pipeline.addLast("##fileInput", new ChunkedHandler(command));
        pipeline.addLast("##exceptionHandler", new ExceptionHandler());
    }

    private static void cleanPipeline(ChannelPipeline pipeline) {

        pipeline.toMap().forEach((K, V) -> {
            if (K.contains("##")) {
                pipeline.remove(K);
            }
        });
    }

    protected static void closeChannel(Channel channel) {
        channel.close();
        String response = "Connection was terminated. Channel closed.";
        log.info(response);
        controller.showLoginWindow();
    }


//    public void start (Controller control) {
//        start(control, null);
//    }

    public static void stop() {
        log.info("Network service was shutdown.");
        if (mainThread != null && !mainThread.isInterrupted()) {
            mainThread.interrupt();
        }
        if (channel != null) {
            channel.close();
        }
    }

    public static void sendAuthInfo (String login, String password) {

        if (!isNetworkServiceWorking) {
            NettyClient.controller.startNetworkService(() -> sendAuthInfo(login, password));
            return;
        }

        if (!isNetworkServiceBusy) {
            isNetworkServiceBusy = true;
            channel.writeAndFlush(new DatabaseCommand(
                    Commands.AUTH_REQUEST,
                    null,
                    login,
                    password,
                    false,
                    null
            ));
        }
    }

    public static void sendRegisterInfo (String username, String login, String password) {

        if (!isNetworkServiceWorking) {
            NettyClient.controller.startNetworkService(() -> sendRegisterInfo(username, login, password));
            return;
        }

        if (!isNetworkServiceBusy) {
            isNetworkServiceBusy = true;
            channel.writeAndFlush(new DatabaseCommand(
                    Commands.REGISTER_REQUEST,
                    username,
                    login,
                    password,
                    false,
                    null
            ));
        }

    }

    public static void sendTransferNotification(Path filePath) {

        if (!isNetworkServiceBusy) {
            isNetworkServiceBusy = true;
            try {
                channel.writeAndFlush(new TransferCommand(
                        Commands.TRANSFER_FILE_NOTIFICATION,
                        NettyClient.controller.serverInnerPath.resolve(filePath).toFile(),
                        Files.size(clientBasePath.resolve(filePath))
                ));
            } catch (NoSuchFileException e) {
                String response = String.format("File '%s' was not found. %s\n", filePath, e);
                NettyClient.controller.consoleLog.appendText(response);
                log.error(response);
                isNetworkServiceBusy = false;
            } catch (IOException e) {
                String response = String.format(
                        "Some I/O error happened when sending notification for transferring '%s'. %s\n", filePath, e);
                NettyClient.controller.consoleLog.appendText(response);
                log.error(response);
                isNetworkServiceBusy = false;
            }
        }
    }

    public static void transfer(Path filePath) {

        fileTransmitPipeline(channel.pipeline());
        try {
            channel.writeAndFlush(new ChunkedFile(filePath.toFile()));
            String response = String.format("Transferring file '%s' has started.\n", filePath);
            NettyClient.controller.consoleLog.appendText(response);
            log.error(response.trim());
        } catch (NoSuchFileException e) {
            String response = String.format("File '%s' was not found. %s\n", filePath, e);
            NettyClient.controller.consoleLog.appendText(response);
            log.error(response.trim());
            isNetworkServiceBusy = false;
        } catch (IOException e) {
            String response = String.format("Some I/O error happened when transferring the file '%s'. %s\n", filePath, e);
            NettyClient.controller.consoleLog.appendText(response);
            log.error(response.trim());
            isNetworkServiceBusy = false;
        }
        commandReadyPipeline(channel.pipeline());
    }

    public static void sendDataStructureRequest(Path innerPath) {

        if (!isNetworkServiceBusy) {
            isNetworkServiceBusy = true;
            channel.writeAndFlush(new FileListCommand(
                    Commands.DATA_STRUCTURE_REQUEST,
                    innerPath.toFile(),
                    null
            ));
        }
    }

    public static void sendReceiveRequest(Path innerPath) {

        if (!isNetworkServiceBusy) {
            isNetworkServiceBusy = true;
            channel.writeAndFlush(new ReceiveCommand(
                    Commands.RECEIVE_FILE_REQUEST_INFO,
                    innerPath.toFile(),
                    0
            ));
        }
    }


    public static void sendRenameRequest(Path innerPathSource, Path innerPathTarget) {

        if (!isNetworkServiceBusy) {
            isNetworkServiceBusy = true;
            channel.writeAndFlush(new RenameCommand(
                    Commands.RENAME_REQUEST,
                    innerPathSource.toFile(),
                    innerPathTarget.toFile(),
                    false,
                    null
            ));
        }

    }

    public static void sendDeleteRequest(Path innerPath) {

        if (!isNetworkServiceBusy) {
            isNetworkServiceBusy = true;
            channel.writeAndFlush(new DeleteCommand(
                    Commands.DELETE_REQUEST,
                    innerPath.toFile(),
                    false,
                    null
            ));
        }

    }

    public static void sendMkdirRequest(Path innerPath) {

        if (!isNetworkServiceBusy) {
            isNetworkServiceBusy = true;
            channel.writeAndFlush(new MkdirCommand(
                    Commands.MKDIR_REQUEST,
                    innerPath.toFile(),
                    false,
                    null
            ));
        }

    }



}
