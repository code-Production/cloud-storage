package com.geekbrains.server;

import com.geekbrains.common.FileListCommand;
import com.geekbrains.common.TransferCommand;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
public class NettyServer {

    public static Path serverBasePath = Paths.get("server/files");
    private static final UserService userService = new DatabaseUserServiceImpl();
    private static final Map<Channel, String> clientsMap = new HashMap<>();


    public static void main(String[] args) {

        EventLoopGroup auth = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            ChannelFuture future = bootstrap.group(auth, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            log.info("New unauthorized client connected to cloud.");
                            authAndRegReadyPipeline(channel.pipeline()); //, clientsMap
                        }

                    }).bind(8189).sync();
            log.info("Server started.");
            userService.start();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("Server thread was interrupted, {}", e.getMessage());
        } finally {
            userService.stop();
            auth.shutdownGracefully();
            worker.shutdownGracefully();
            log.info("Server stopped.");
        }
    }

    protected static void commandReadyPipeline(ChannelPipeline pipeline) {
        cleanPipeline(pipeline);
        pipeline.addLast("##commandInput", new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
        pipeline.addLast("##commandOutput", new ObjectEncoder());//if lower than CommandHandler() then it doesn't work !?
        pipeline.addLast("##commandInputHandler", new CommandHandler());
        pipeline.addLast("##exceptionHandler", new ExceptionHandler());

    }

    protected static void authAndRegReadyPipeline(ChannelPipeline pipeline) { //, Map<Channel, String> clientsMap
        cleanPipeline(pipeline);
        pipeline.addLast("##commandInput", new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
        pipeline.addLast("##commandOutput", new ObjectEncoder());
        pipeline.addLast("##AuthAndRegHandler", new AuthAndRegHandler(userService));
        pipeline.addLast("##exceptionHandler", new ExceptionHandler());
    }

    protected static void fileReceivePipeline(TransferCommand command, ChannelPipeline pipeline) {

        cleanPipeline(pipeline);
        pipeline.addLast("##commandOutput", new ObjectEncoder());
        pipeline.addLast("##fileInput", new ChunkedHandler(command));
        pipeline.addLast("##exceptionHandler", new ExceptionHandler());

    }

    protected static void fileTransmitPipeline(ChannelPipeline pipeline) {

        cleanPipeline(pipeline);
        pipeline.addLast("##commandInput", new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
        pipeline.addLast("##fileOutput", new ChunkedWriteHandler());
        pipeline.addLast("##exceptionHandler", new ExceptionHandler());

    }

    protected static void cleanPipeline(ChannelPipeline pipeline) {

        pipeline.toMap().forEach((K, V) -> {
            if (K.contains("##")) {
                pipeline.remove(K);
            }
        });

    }


    private static Path getValidPath(Channel channel, Path innerPath) {

        String innerPathStr = innerPath.toString();
        Path zeroPath = Paths.get("");

        if (innerPathStr.equals("..") || innerPathStr.equals("") || innerPathStr.equals("\\")) {
//            System.out.println(innerPathStr);
            return zeroPath;
        }
        if (innerPathStr.contains("..")) {
            innerPathStr = innerPathStr.replace("..", "");
            innerPath = Paths.get(innerPathStr).getParent();
            if (innerPath == null) {
                return zeroPath;
            }
        }

        Path newPath;
        while (true) {
//            newPath = serverBasePath.resolve(innerPath);
            newPath = getClientFolderPath(channel).resolve(innerPath);
            if (!Files.isReadable(newPath) && !Files.isDirectory(newPath)) {
                innerPath = innerPath.getParent();
                if (innerPath == null) {
                    return zeroPath; //root
                }
            } else {
                return innerPath;
            }
        }

    }

    protected static void writeFolderStructure(Channel channel, FileListCommand command) throws IOException {

        Path innerPath = getValidPath(channel, command.getFolder().toPath());
        Path fullPath = getClientFolderPath(channel).resolve(innerPath);


        try (Stream<Path> pathStream = Files.list(fullPath)) {

            Function<Path, String> mapper = V -> {
                String name = V.getFileName().toString();
                if (Files.isDirectory(V)) {
                    name = String.format("%s[DIR]", name);
                }
                return name;
            };

            List<String> list = pathStream.map(mapper).toList();
//            System.out.println(list);
            command.setFolder(innerPath.toFile());
            command.setFolderStructure(list);

        } catch (IOException e) {
            log.error("Cannot get base folder structure, {}.", e.getMessage());
            command.setFolder(null);
            command.setFolderStructure(null);
        }

    }

    protected synchronized static void addClient(Channel channel, String login) {
        log.debug("Client '{}' was added to client map.", login);
        clientsMap.put(channel, login);
    }

    protected synchronized static void removeClient(Channel channel) {
        String key = clientsMap.remove(channel);
        log.debug("Client '{}' was removed from client map.", key);
    }

    protected static Path getClientFolderPath(Channel channel) {
        return serverBasePath.resolve(String.format("[%s]", clientsMap.get(channel)));
    }

    protected static void closeChannel(String source, Channel channel) {
        channel.close();
        String userFolder = clientsMap.get(channel);
        String response = String.format("%s: client '%s' disconnected. Channel closed.", source, userFolder);
        log.info(response);
        removeClient(channel);
    }

    protected static void createUserFolderIfNotExists(Channel channel) {
        Path userPath = getClientFolderPath(channel);
//        System.out.println(userPath);
//        System.out.println("Files.exists(userPath) " + Files.exists(userPath));
        if (!Files.exists(userPath)) {
            try {
                Files.createDirectory(userPath);
            } catch (FileAlreadyExistsException e) {
                NettyServer.closeChannel(
                        String.format(
                                "Cannot create user directory '%s' because it already exists, exception='%s'",
                                userPath,
                                e.getMessage()
                        ),
                        channel
                );
            } catch (IOException e) {
                if (!Files.exists(userPath.getParent())) {
                    try {
                        Files.createDirectories(userPath.getParent());
                    } catch (IOException ex) {
                        NettyServer.closeChannel(
                                String.format(
                                        "Creating parent directory for user's folders '%s' caused I/O error.",
                                        userPath
                                ),
                                channel
                        );
                    }

                } else {
                    NettyServer.closeChannel(
                            String.format("Unknown IO problem occurred, exception='%s'", e.getMessage()),
                            channel
                    );
                }
            }
        } else {
//            System.out.println("Files.isDirectory(userPath) " + Files.isDirectory(userPath));
            if (!Files.isDirectory(userPath)) {
                log.error(String.format("Cannot create user folder because file with this name '%s' already exists.",
                        userPath.getFileName()));
                NettyServer.closeChannel("createUserFolderIfNotExists", channel);
            }
        }
    }

}
