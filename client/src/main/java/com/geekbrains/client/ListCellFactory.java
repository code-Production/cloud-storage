package com.geekbrains.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import static com.geekbrains.client.AppStarter.clientBasePath;


@Slf4j
public class ListCellFactory implements Callback<ListView<String>, ListCell<String>> {

    private static final int ICON_SIZE = 16;
//    private static final String FOLDER_ICON = "client/src/main/resources/com/geekbrains/client/folder32-2.png";
//    private static final String FILE_ICON = "client/src/main/resources/com/geekbrains/client/file32-2.png";
    //doesn't work in intelliJ
    //otherwise images doesn't render in jar after maven assembly
    private static final String FOLDER_ICON = "/folder32-2.png";
    private static final String FILE_ICON = "/file32-2.png";


    @Override
    public ListCell<String> call(ListView<String> param) {

        return new ListCell<>() {

            final ImageView imageView = new ImageView();
            final Label title = new Label();
            final HBox rootLayout = new HBox(5) {{
                setAlignment(Pos.CENTER_LEFT);
                setPadding(new Insets(5));
            }};
            //without file container relative path doesn't work with images
            final File folderFile = new File(FOLDER_ICON);
            final File docsFile = new File(FILE_ICON);
            final ContextMenu contextMenuClient = new ContextMenu();
            final ContextMenu contextMenuServer = new ContextMenu();
            final MenuItem uploadItem = new MenuItem();
            final MenuItem downloadItem = new MenuItem();
            final MenuItem renameItemClient = new MenuItem();
            final MenuItem renameItemServer = new MenuItem();
            final MenuItem deleteItemClient = new MenuItem();
            final MenuItem deleteItemServer = new MenuItem();
            final MenuItem mkdirItemClient = new MenuItem();
            final MenuItem mkdirItemServer = new MenuItem();

            //initializing inner variables
            {
                rootLayout.getChildren().addAll(imageView, title);
                contextMenuClient.setStyle("-fx-min-width: 100;");
                contextMenuServer.setStyle("-fx-min-width: 100;");

                //TODO maybe move handlers to controller class
                //client context menu
                uploadItem.textProperty().set("Upload");
                //uploadItem must be active only for files not folders!
                uploadItem.setOnAction((event) -> {
                    //check if file already presented in cloud and confirm replacement
                    String selected = NettyClient.controller.clientFilesList.getSelectionModel().getSelectedItem();
                    if (NettyClient.controller.fileAlreadyExistsOnServer(selected)) {
                        NettyClient.controller.showIfYesThenRun(
                                String.format("File '%s' already exists on cloud. Replace?\n", selected),
                                () -> {
                                    String response = String.format("File '%s' will replace the one on cloud.\n", selected);
                                    log.debug(response.trim());
                                    NettyClient.controller.consoleLog.appendText(response);
                                    NettyClient.sendTransferNotification(Paths.get(selected));
                                }
                        );
                    } else {
                        NettyClient.sendTransferNotification(Paths.get(selected));
                    }
                });

                renameItemClient.textProperty().set("Rename");
                renameItemClient.setOnAction((event) -> {
                    //TODO detect file or folder
                    NettyClient.controller.showInputWindow(
                            "Renaming process",
                        "Enter new name: ",
                        (newFileNameField) -> {
                            String fileName = NettyClient.controller.clientFilesList.getSelectionModel().getSelectedItem();
                            String newFileName = newFileNameField.getText().trim();
                            //if something selected and new file name is not empty
                            if (fileName != null && !newFileName.equals("")) {
                                String response;
                                //if new filename is not taken
                                if (!NettyClient.controller.fileAlreadyExistsOnClient(newFileName)) {
                                    File newFile = new File(clientBasePath.toString(), newFileName);
                                    //if source file exists (it is actual on client side)
                                    if (Files.isReadable(clientBasePath.resolve(fileName))) {
                                        //if rename operation is success
                                        if (clientBasePath.resolve(fileName).toFile().renameTo(newFile)) {
                                            response = String.format(
                                                    "File '%s' was successfully renamed to '%s'.\n",
                                                    fileName,
                                                    newFileName
                                            );
                                            NettyClient.controller.consoleLog.appendText(response);
                                            log.debug(response.trim());
                                            NettyClient.controller.updateClientFilesList();
                                            return true;
                                        //if not
                                        } else {
                                            response = String.format(
                                                    "Try to rename file '%s' to '%s' failed by unknown reason.\n",
                                                    fileName,
                                                    newFileName
                                            );
                                        }
                                    //if source file is not found
                                    } else {
                                        response = String.format("Source file '%s' was not found.\n", fileName);
                                    }
                                //if new filename is taken
                                } else {
                                    response = String.format("File with this name '%s' already exists.\n", newFileName);
                                }
                                NettyClient.controller.consoleLog.appendText(response);
                                log.debug(response.trim());
                                NettyClient.controller.showAlert(Alert.AlertType.ERROR, response);
                            }
                            return false;
                        }
                    );
                });


                deleteItemClient.textProperty().set("Delete");
                deleteItemClient.setOnAction((event) -> {
                    String selected = NettyClient.controller.clientFilesList.getSelectionModel().getSelectedItem();
                    NettyClient.controller.showIfYesThenRun(
                            String.format("Are you sure you want to delete this file '%s'?", selected),
                            () -> {
                                String response;
                                boolean success = false;
                                try {
                                    Files.delete(clientBasePath.resolve(selected));
                                    success = true;
                                    response = String.format("File '%s' was deleted.\n", selected);
                                } catch (NoSuchFileException e) {
                                    response = String.format("File '%s' is not found.\n", selected);
                                } catch (DirectoryNotEmptyException e) {
                                    response = String.format("Folder '%s' cannot be deleted because it is not empty.\n", selected);
                                } catch (IOException e) {
                                    response = String.format("Unknown I/O error happened, %s.\n", e);
                                }
                                log.debug(response.trim());
                                NettyClient.controller.consoleLog.appendText(response);
                                NettyClient.controller.updateClientFilesList();
                                if (!success) {
                                    NettyClient.controller.showAlert(Alert.AlertType.ERROR, response);
                                }
                            }
                    );
                });


                mkdirItemClient.textProperty().set("New folder");
                mkdirItemClient.setOnAction((event) -> {
                    NettyClient.controller.showInputWindow(
                            "Creating new folder process",
                            "Enter new folder name:",
                            (newNameInputField) -> {
                                String newFolderName = newNameInputField.getText().trim();
                                String response;
                                boolean completed = false;
                                if (!newFolderName.equals("")) {
                                    try {
                                        if (!NettyClient.controller.fileAlreadyExistsOnClient(newFolderName)) {
                                            Files.createDirectory(clientBasePath.resolve(newFolderName));
                                            response = String.format("Folder '%s' successfully created.\n", newFolderName);
                                            completed = true;
                                        } else {
                                            response = String.format("Folder with such name '%s' already exists.\n", newFolderName);
                                            NettyClient.controller.showAlert(Alert.AlertType.WARNING, response);
                                            //completed = false so window stay open
                                        }
                                    } catch (IOException e) {
                                        response = String.format("Unknown I/O error happened, %s.\n", e);
                                        NettyClient.controller.showAlert(Alert.AlertType.WARNING, response);
                                        completed = true; //close window
                                    }

                                    NettyClient.controller.consoleLog.appendText(response);
                                    log.debug(response.trim());
                                    NettyClient.controller.updateClientFilesList();
                                }
                                return completed;
                            }
                    );
                });

                //server context menu
                downloadItem.textProperty().set("Download");
                downloadItem.setOnAction((event) -> {
                    String selected = NettyClient.controller.serverFilesList.getSelectionModel().getSelectedItem();
                    if (NettyClient.controller.fileAlreadyExistsOnClient(selected)) {
                        NettyClient.controller.showIfYesThenRun(
                                String.format("File '%s' already exists on client. Replace?", selected),
                                () -> {
                                    String response = String.format("File '%s' will replace the one on client.\n", selected);
                                    log.debug(response.trim());
                                    NettyClient.controller.consoleLog.appendText(response);
//                                    System.out.println(NettyClient.controller.serverInnerPath.resolve(selected));
                                    NettyClient.sendReceiveRequest(
                                            NettyClient.controller.serverInnerPath.resolve(selected));
                                }
                        );
                    } else {
//                        System.out.println(NettyClient.controller.serverInnerPath.resolve(selected));
                        NettyClient.sendReceiveRequest(
                                NettyClient.controller.serverInnerPath.resolve(selected));
                    }
                });

                renameItemServer.textProperty().set("Rename");
                renameItemServer.setOnAction((event) -> {
                    NettyClient.controller.showInputWindow(
                            "Renaming process",
                            "Enter new file name:",
                            (newNameTextField) -> {
                                String selected = NettyClient.controller.serverFilesList.getSelectionModel().getSelectedItem();
                                selected = selected.replace("[DIR]", "");
                                String newName = newNameTextField.getText().trim();
                                if (selected != null && !newName.equals("")) {
                                    Path innerPathSource = NettyClient.controller.serverInnerPath.resolve(selected);
                                    Path innerPathTarget = NettyClient.controller.serverInnerPath.resolve(newName);
                                    if (!NettyClient.controller.fileAlreadyExistsOnServer(newName)) {
//                                        System.out.println("sendRenameRequest");
                                        NettyClient.sendRenameRequest(innerPathSource, innerPathTarget);
                                    } else {
                                        String response = String.format(
                                                "File with this name '%s' already exists on cloud.\n",
                                                newName
                                        );
                                        NettyClient.controller.showAlert(Alert.AlertType.WARNING, response);
                                        log.debug(response.trim());
                                        NettyClient.controller.consoleLog.appendText(response);
                                    }
                                }
                                return false;
                            }
                    );
                });

                deleteItemServer.textProperty().set("Delete");
                deleteItemServer.setOnAction((event) -> {
                    NettyClient.controller.showIfYesThenRun(
                            "Are you sure you want to delete this file?",
                            () -> {
                                String selected = NettyClient.controller.serverFilesList.getSelectionModel().getSelectedItem();
                                selected = selected.replace("[DIR]", "");
                                NettyClient.sendDeleteRequest(NettyClient.controller.serverInnerPath.resolve(selected));
                            }
                    );
                });


                mkdirItemServer.textProperty().set("New folder");
                mkdirItemServer.setOnAction((event) -> {
                    NettyClient.controller.showInputWindow(
                            "Creating new folder process",
                            "Enter new folder name: ",
                            (newNameTextField) -> {
                                String newFolderName = newNameTextField.getText().trim();
                                String response;
                                boolean completed = false;
                                if (!newFolderName.equals("")){
//                                    System.out.println("BOOLEAN:" + NettyClient.controller.fileAlreadyExistsOnServer(newFolderName));
                                    if (!NettyClient.controller.fileAlreadyExistsOnServer(newFolderName)) {
                                        NettyClient.sendMkdirRequest(
                                                NettyClient.controller.serverInnerPath.resolve(newFolderName));
                                    } else {
                                        response = String.format(
                                                "Folder with such name '%s' already exists in cloud.\n",
                                                newFolderName
                                        );
                                        NettyClient.controller.showAlert(Alert.AlertType.ERROR, response);
                                        log.debug(response.trim());
                                        NettyClient.controller.consoleLog.appendText(response);
                                    }
                                }
                                return completed;
                            }
                    );
                });

                contextMenuClient.getItems().addAll(uploadItem, mkdirItemClient, renameItemClient, deleteItemClient);
                contextMenuServer.getItems().addAll(downloadItem, mkdirItemServer, renameItemServer, deleteItemServer);
            }

            @Override
            protected void updateItem(String item, boolean empty) {

                super.updateItem(item, empty);

                try {

                    if (this.getListView().getId().equals("clientFilesList")) {
                        this.setContextMenu(contextMenuClient);
                    } else if (this.getListView().getId().equals("serverFilesList")) {
                        this.setContextMenu(contextMenuServer);
                    }

                    if (item != null && !empty) {

                        mkdirItemClient.setDisable(false);
                        renameItemClient.setDisable(false);
                        deleteItemClient.setDisable(false);
                        mkdirItemServer.setDisable(false);
                        renameItemServer.setDisable(false);
                        deleteItemServer.setDisable(false);

                        //server side folder detector
                        if (item.contains("[DIR]")) {
                            item = item.replace("[DIR]", "");
                            imageView.setImage(new Image(folderFile.toURI().toString()));
                            uploadItem.setDisable(true);
                            downloadItem.setDisable(true);
                        //client side folder detector
                        } else if (Files.isDirectory(clientBasePath.resolve(item))) {
                            imageView.setImage(new Image(folderFile.toURI().toString()));
                            uploadItem.setDisable(true);
                            downloadItem.setDisable(true);
                        } else {
                            imageView.setImage(new Image(docsFile.toURI().toString()));
                            uploadItem.setDisable(false);
                            downloadItem.setDisable(false);
                        }

                        if (item.contains("..")) {
                            renameItemClient.setDisable(true);
                            deleteItemClient.setDisable(true);
                            renameItemServer.setDisable(true);
                            deleteItemServer.setDisable(true);
                        }

                        imageView.setPreserveRatio(true);
                        imageView.setFitHeight(ICON_SIZE);
                        imageView.setFitWidth(ICON_SIZE);
                        setGraphic(rootLayout);
                        title.setText(item);

                    } else {
                        uploadItem.setDisable(true);
                        downloadItem.setDisable(true);
                        mkdirItemClient.setDisable(false);
                        renameItemClient.setDisable(true);
                        deleteItemClient.setDisable(true);
                        mkdirItemServer.setDisable(false);
                        renameItemServer.setDisable(true);
                        deleteItemServer.setDisable(true);

                        setGraphic(null);
                        title.setText(null);

                    }
                } catch (IllegalArgumentException e) {
                    log.error("Couldn't find image files. ", e);
                }
            }

        };

    }
}
