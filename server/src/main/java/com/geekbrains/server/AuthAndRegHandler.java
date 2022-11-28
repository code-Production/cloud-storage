package com.geekbrains.server;

import com.geekbrains.common.AbstractCommand;
import com.geekbrains.common.Commands;
import com.geekbrains.common.DatabaseCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;

@Slf4j
public class AuthAndRegHandler extends SimpleChannelInboundHandler<AbstractCommand> {

    private final UserService userService;

    public AuthAndRegHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractCommand msg) throws Exception {
        switch(msg.getCommand()) {
            case AUTH_REQUEST -> {
                DatabaseCommand command = (DatabaseCommand) msg;
                command.setCommand(Commands.AUTH_RESPONSE);
                String login = command.getLogin();
                String password = command.getPassword();
                log.debug("Got new authentication command from client '{}'.", login);
                boolean isSuccess = false;
                try {
                    String username = userService.authenticate(login, password);
                    if (username != null) {
                        isSuccess = true;
                        command.setUsername(username);
                        //check folder with createUserFolderIfNotExists() if problem it disconnects
                        //it uses clientMap to navigate to user's folder so addClient() is mandatory
                        NettyServer.addClient(ctx.channel(), login);
                        NettyServer.createUserFolderIfNotExists(ctx.channel());
                    }
                    command.setSuccess(isSuccess);
                } catch (SQLException e) {
                    String response = String.format("Authentication process caused SQL exception, %s.", e);
                    command.setResponse(response);
                    log.error(response);
                }
                ctx.writeAndFlush(command);
                if (isSuccess) {
                    log.info("Client '{}' successfully logged in.", login);
                    NettyServer.commandReadyPipeline(ctx.pipeline());
                } else {
                    log.info("Client '{}' couldn't logged in.", login);
                }
//                System.out.println("AUTH_REQUEST");
            }
            case REGISTER_REQUEST -> {
                DatabaseCommand command = (DatabaseCommand) msg;
                command.setCommand(Commands.REGISTER_RESPONSE);
                String login = command.getLogin();
                String password = command.getPassword();
                String username = command.getUsername();
                log.debug("Got new registration command from client '{}'.", login);
                boolean isSuccess = false;
                String response;
                try {
                    isSuccess = userService.register(username, login, password);
                    command.setSuccess(isSuccess);
                    if (isSuccess) {
                        response = String.format("Client '%s' successfully registered.", login);
                    } else {
                        response = String.format("Client '%s' couldn't register (duplicate credentials).", login);
                    }
                    log.info(response);
                } catch (SQLException e) {
                    response = String.format("Registration process caused SQL exception, %s.\n", e);
                    command.setResponse(response);
                    log.error(response.trim());
                }
                ctx.writeAndFlush(command);
//                System.out.println("REGISTER_REQUEST");
            }
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyServer.closeChannel("AuthAndRegHandler", ctx.channel());
    }

}
