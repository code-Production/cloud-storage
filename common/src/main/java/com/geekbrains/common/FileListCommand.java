package com.geekbrains.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
public class FileListCommand extends AbstractCommand{

    private File folder;
    private List<String> folderStructure;


    public FileListCommand(Commands command,File folder, List<String> folderStructure) {
        super(command);
        this.folder = folder;
        this.folderStructure = folderStructure;
    }

    public void setFolder(File folder) {
        this.folder = folder;
    }

    public List<String> getFolderStructure() {
        return folderStructure;
    }

    public File getFolder() {
        return folder;
    }

    public void setFolderStructure(List<String> folderStructure) {
        this.folderStructure = folderStructure;
    }
}
