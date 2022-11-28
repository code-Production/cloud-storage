package com.geekbrains.common;

public class DatabaseCommand extends AbstractCommand{

    private String username;
    private String login;
    private String password;
    private boolean isSuccess;
    private String response;

    public DatabaseCommand(Commands command, String username, String login, String password, boolean isSuccess, String response) {
        super(command);
        this.username = username;
        this.login = login;
        this.password = password;
        this.isSuccess = isSuccess;
        this.response = response;
    }

    public String getUsername() {
        return username;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String getResponse() {
        return response;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
