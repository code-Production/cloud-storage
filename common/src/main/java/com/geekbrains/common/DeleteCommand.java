package com.geekbrains.common;

import java.io.File;

public class DeleteCommand extends AbstractCommand{

    private File file;
    private boolean isSuccess;
    private String response;

    public DeleteCommand(Commands command, File file, boolean isSuccess, String response) {
        super(command);
        this.file = file;
        this.isSuccess = isSuccess;
        this.response = response;
    }

    public File getFile() {
        return file;
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
}
