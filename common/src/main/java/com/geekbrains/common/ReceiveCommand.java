package com.geekbrains.common;

import java.io.File;

public class ReceiveCommand extends AbstractCommand{

    private File file;

    private long fileSize;


    public ReceiveCommand(Commands command, File file, long fileSize) {
        super(command);
        this.file = file;
        this.fileSize = fileSize;
    }

    public File getFile() {
        return file;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getFileSize() {
        return fileSize;
    }
}
