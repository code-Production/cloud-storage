package com.geekbrains.common;

import java.io.File;

public class MkdirCommand extends AbstractCommand{

    private File folderFile;
    private boolean isSuccess;
    private String response;

    public MkdirCommand(Commands command, File folderFile, boolean isSuccess, String response) {
        super(command);
        this.folderFile = folderFile;
        this.isSuccess = isSuccess;
        this.response = response;
    }

    public File getFolderFile() {
        return folderFile;
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
