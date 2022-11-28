package com.geekbrains.server;

import java.sql.SQLException;

public interface UserService {

    void start();
    void stop();
    String authenticate(String login, String password) throws SQLException;
    boolean register(String username, String login, String password) throws SQLException;

}
