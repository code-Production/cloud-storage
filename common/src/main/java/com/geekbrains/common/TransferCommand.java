package com.geekbrains.common;

import lombok.EqualsAndHashCode;
import java.io.File;

@EqualsAndHashCode(callSuper = true)
public class TransferCommand extends AbstractCommand {

    private File file;
    private long fileSize;

    public TransferCommand(Commands command, File file, long fileSize) {
        super(command);
        this.file = file;
        this.fileSize = fileSize;
    }

    public File getFile() {
        return file;
    }

    public long getFileSize() {
        return fileSize;
    }
}
