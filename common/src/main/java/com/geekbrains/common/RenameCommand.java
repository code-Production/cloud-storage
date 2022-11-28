package com.geekbrains.common;


import java.io.File;

public class RenameCommand extends AbstractCommand{

    private File sourceFile;
    private File newFile;
    private boolean isSuccess;
    private String response;

    public RenameCommand(Commands command, File sourceFile, File newFile, boolean isSuccess, String response) {
        super(command);
        this.sourceFile = sourceFile;
        this.newFile = newFile;
        this.isSuccess = isSuccess;
        this.response = response;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public File getNewFile() {
        return newFile;
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
