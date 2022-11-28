package com.geekbrains.client;

import com.geekbrains.common.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.file.Paths;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import static com.geekbrains.client.AppStarter.clientBasePath;
import static com.geekbrains.client.AppStarter.isNetworkServiceBusy;

@Slf4j
public class CommandHandler extends SimpleChannelInboundHandler<AbstractCommand> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractCommand msg) throws Exception {

        switch (msg.getCommand()) {
            case TRANSFER_FILE_READY -> {
                TransferCommand command = (TransferCommand) msg;
                log.debug("Got TRANSFER_FILE_READY command");
//                NettyClient.transfer(clientBasePath.resolve(command.getFile().toPath()));
                NettyClient.transfer(clientBasePath.resolve(Paths.get(command.getFile().getName())));
            }
            case TRANSFER_FILE_OK -> {
                TransferCommand command = (TransferCommand) msg;
                String response = String.format(
                        "File '%s' was successfully uploaded to the cloud.\n",
                        command.getFile().getName()
                );
                NettyClient.controller.consoleLog.appendText(response);
                log.info(response.trim());
                isNetworkServiceBusy = false;
                NettyClient.sendDataStructureRequest(NettyClient.controller.serverInnerPath);
            }
            case DATA_STRUCTURE_RESPONSE -> {
                FileListCommand command = (FileListCommand) msg;
                log.debug("Got DATA_STRUCTURE_RESPONSE command");
                Platform.runLater(() -> {
                    String serverInnerPathStr = command.getFolder().toString();
                    NettyClient.controller.serverPathField.setText(serverInnerPathStr);
                    NettyClient.controller.serverInnerPath = Paths.get(serverInnerPathStr);
                    NettyClient.controller.updateServerFilesList(command.getFolderStructure());
                });
                isNetworkServiceBusy = false;
            }
            case RECEIVE_FILE_INFO ->  {
                ReceiveCommand command = (ReceiveCommand) msg;
                log.debug("Got RECEIVE_FILE_INFO command.");
                command.setCommand(Commands.RECEIVE_FILE_READY);
                ctx.writeAndFlush(command);
                NettyClient.fileReceivePipeline(command, ctx.pipeline()); //pipeline will be set to command in ChunkedHandler()
            }
            case RENAME_RESPONSE -> {
                RenameCommand command = (RenameCommand) msg;
//                File sourceFile = command.getSourceFile();
//                String newName = command.getNewFile().getName();
                String response = command.getResponse();

                isNetworkServiceBusy = false;
                if (command.isSuccess()) {
                    Stage miniStage = NettyClient.controller.inputWindowController.getStage();
                    if (miniStage != null) {
                        Platform.runLater(miniStage::close);
                    }
                    NettyClient.sendDataStructureRequest(NettyClient.controller.serverInnerPath);
                } else {
                    NettyClient.controller.showAlert(Alert.AlertType.WARNING, response);
                }
                NettyClient.controller.consoleLog.appendText(response);
                log.debug(response.trim());
            }
            case DELETE_RESPONSE -> {
                DeleteCommand command = (DeleteCommand) msg;
                String response = command.getResponse();

                if (!command.isSuccess()) {
                    NettyClient.controller.showAlert(Alert.AlertType.ERROR, response);
                }
                log.debug(response.trim());
                NettyClient.controller.consoleLog.appendText(response);
                isNetworkServiceBusy = false;
                NettyClient.sendDataStructureRequest(NettyClient.controller.serverInnerPath);
            }
            case MKDIR_RESPONSE -> {
                MkdirCommand command = (MkdirCommand) msg;
                String response = command.getResponse();
                isNetworkServiceBusy = false;
                if (command.isSuccess()) {
                    Stage miniStage = NettyClient.controller.inputWindowController.getStage();
                    if (miniStage != null) {
                        Platform.runLater(miniStage::close);
                    }
                    NettyClient.sendDataStructureRequest(NettyClient.controller.serverInnerPath);
                } else {
                    NettyClient.controller.showAlert(Alert.AlertType.ERROR, response);
                }
                log.debug(response.trim());
                NettyClient.controller.consoleLog.appendText(response);
            }
            case AUTH_RESPONSE -> {
                DatabaseCommand command = (DatabaseCommand) msg;
                isNetworkServiceBusy = false;
                if (command.isSuccess()) {
                    Platform.runLater(() -> {
                        NettyClient.controller.showMainWindow();
                        NettyClient.controller.getMainStage().setTitle(
                                String.format("Cloud storage client [%s]", command.getUsername())
                        );
                        if (clientBasePath.toString().equals("")) {
                            NettyClient.controller.setClientFilesListToRoot();
                        } else {
                            NettyClient.controller.updateClientFilesList();
                        }

                        NettyClient.sendDataStructureRequest(NettyClient.controller.serverInnerPath);
                    });
                    log.info("You have successfully logged in as {}.", command.getLogin());
                } else if (command.getResponse() == null){
                    String response = "You've entered wrong login/password.";
                    NettyClient.controller.showAlert(Alert.AlertType.WARNING, response);
                    log.debug(response);
                } else {
                    String response = "Unknown SQL error occurred.";
                    NettyClient.controller.showAlert(Alert.AlertType.ERROR, response);
                    log.debug(response);
                }
            }
            case REGISTER_RESPONSE -> {
                DatabaseCommand command = (DatabaseCommand) msg;
                isNetworkServiceBusy = false;
                if (command.isSuccess()) {
                    NettyClient.controller.showLoginWindow();
                    log.info("You have successfully registered as {}.", command.getLogin());
                } else if (command.getResponse() == null){
                    String response = "User with those credentials already exists.";
                    NettyClient.controller.showAlert(Alert.AlertType.WARNING, response);
                    log.debug(response);
                } else {
                    String response = "Unknown SQL error occurred.";
                    NettyClient.controller.showAlert(Alert.AlertType.ERROR, response);
                    log.debug(response);
                }
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyClient.closeChannel(ctx.channel());
    }

}
