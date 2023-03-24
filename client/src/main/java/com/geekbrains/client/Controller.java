package com.geekbrains.client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import static com.geekbrains.client.AppStarter.clientBasePath;

@Slf4j
public class Controller implements Initializable {

    public VBox registerWindow;
    public VBox loginWindow;
    public VBox mainWindow;
    public TextField loginFieldLoginWindow;
    public PasswordField passwordFieldLoginWindow;
    public TextField usernameFieldRegisterWindow;
    public TextField loginFieldRegisterWindow;
    public PasswordField passwordFieldRegisterWindow;
    public ListView<String> serverFilesList;

    public TextArea consoleLog;
    public ListView<String> clientFilesList;
    public TextField clientPathField;
    public TextField serverPathField;

    public InputWindowController inputWindowController;
    private ListCellFactory factory;
    protected Path serverInnerPath = Paths.get("");

    protected Stage mainStage;

    public void setMainStage(Stage mainStage) {
        this.mainStage = mainStage;
    }

    public Stage getMainStage() {
        return mainStage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        NettyClient.start(this, null);
        factory = new ListCellFactory();
        serverFilesList.setCellFactory(factory);
        clientFilesList.setCellFactory(factory);

//        updateClientFilesList();
        initClientFilesListListener();
        initServerFilesListListener();

    }

    public void closeApplication(ActionEvent actionEvent) {
        NettyClient.stop();
    }

    private void initServerFilesListListener() {

        serverFilesList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedString = serverFilesList.getSelectionModel().getSelectedItem();
                if (selectedString != null) {
                    if (selectedString.contains("[DIR]") || selectedString.equals("..")){
                        selectedString = selectedString.replace("[DIR]", "");

                        serverInnerPath = serverInnerPath.resolve(selectedString);
//                        System.out.println(serverInnerPath);
                        NettyClient.sendDataStructureRequest(serverInnerPath);
                    }
                }
            }
        });

    }

    protected void updateServerFilesList(List<String> list) {

        serverFilesList.getItems().clear();
        serverFilesList.getItems().add("..");
        serverFilesList.getItems().addAll(list);

    }

    private void initClientFilesListListener() {

        clientFilesList.setOnMouseClicked((e) -> {
            if (e.getClickCount() == 2) {
                String selectedString = clientFilesList.getSelectionModel().getSelectedItem();
                //if nothing is selected
                if (selectedString != null) {
                    Path newBasePath;
                    if (selectedString.equals("..")) {
                        newBasePath = clientBasePath.getParent();
                        if (newBasePath == null) {
                            setClientFilesListToRoot();
                            return;
                        }
                    } else {
                        newBasePath = clientBasePath.resolve(Paths.get(selectedString));
//                        System.out.println(newBasePath);
                    }
                    if (Files.isDirectory(newBasePath)) {
//                        System.out.println("directory");
                        clientFilesList.scrollTo(0);
                        clientBasePath = newBasePath;
//                        consoleLog.clear();
                        updateClientFilesList();
                        clientPathField.setText(clientBasePath.toString());
                    }
//                    System.out.println(clientBasePath);
                }
            }
        });

    }

    public void updateClientFilesList() {

        clientFilesList.getItems().clear();
        clientFilesList.getItems().add("..");
        try (Stream<Path> streamPath = Files.list(clientBasePath)) {
            clientFilesList.getItems().addAll(streamPath.map((path) -> path.getFileName().toString()).toList());
        } catch (AccessDeniedException e) {
            consoleLog.appendText("Access to this folder was denied by system.\n");
        } catch (IOException e) {
            String response = "Unknown I/O error occurred while updating client file list.\n";
            consoleLog.appendText(response);
            log.error(response.trim());
        }
    }

    public void setClientFilesListToRoot() {
        clientFilesList.getItems().clear();
        clientFilesList.getItems().addAll(Arrays.stream(File.listRoots()).map(File::toString).toList());
        clientPathField.clear();
        clientBasePath = Paths.get(""); // seems ok
    }

    @FXML
    protected void setClientPath(ActionEvent actionEvent) throws IOException {
        String text = clientPathField.getText().trim();
        Path newPath;

        if (text.equals("..")) {
            // move one folder up from last valid path 'basePath' - same function as cell '..'
            newPath = clientBasePath.getParent();
        } else if (text.contains("..")) {
            //move one folder up from new path in 'text'
            text = text.replace("..", "");
            newPath = Paths.get(text).getParent();
        } else if (text.equals("") || text.equals("/")) {
            //easy root
            setClientFilesListToRoot();
            return;
        } else {
            //standard path to somewhere
            newPath = Paths.get(text);
        }

        //if there is no parent when .getParent();
        if (newPath == null) {
//            clientPathField.setText(clientBasePath.toString());
            setClientFilesListToRoot();
            return;
        }

        //mechanism to find the closest valid folder to path in 'text' if possible
        if (!Files.isReadable(newPath) || !Files.isDirectory(newPath)) {
            while(true) {
                newPath = newPath.getParent();
                if (newPath == null) {
                    consoleLog.appendText(String.format("No such folder '%s' exists. Returned to root.\n", text));
//                    clientPathField.setText(clientBasePath.toString()); //basePath
//                    updateClientFilesList();
                    setClientFilesListToRoot();
                    return;
                }
                if (Files.isReadable(newPath)) {
                    break;
                }
            }
        }
        clientBasePath = newPath;


        clientPathField.setText(clientBasePath.toString());
        updateClientFilesList();
//        consoleLog.clear();


    }

    public void setServerPath(ActionEvent actionEvent) {
//        System.out.println("INPUT: " + serverPathField.getText());
        NettyClient.sendDataStructureRequest(Paths.get(serverPathField.getText().trim()));
    }

    public void showIfYesThenRun(String message, Runnable runnable) {

        Platform.runLater(() -> {
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    message,
                    ButtonType.YES,
                    ButtonType.NO);
            alert.showAndWait();

            if (alert.getResult().equals(ButtonType.YES)) {
                runnable.run();
            }
        });
    }

    public void showAlert(Alert.AlertType type, String message) {
        Platform.runLater(() -> {
            new Alert(type, message, ButtonType.CLOSE).showAndWait();
        });
    }

    public void showInputWindow(String title, String message, RunnableInputWindow runnableWindow) {


        Stage extraStage = new Stage();
        //TODO maybe make it settable
        extraStage.setTitle("Cloud storage input window");
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(this.getClass().getResource("/cloud-storage-client-input-window.fxml"));
        try {
            Parent parent = loader.load();
            inputWindowController = loader.getController();
            extraStage.setTitle(title);
            inputWindowController.setStage(extraStage);
            inputWindowController.setInputWindowLabel(message);
            inputWindowController.setRunnableInputWindow(runnableWindow);
            Scene extraScene = new Scene(parent);
            extraStage.setScene(extraScene);
            extraStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean fileAlreadyExistsOnServer(String fileName) {
        for (String s : serverFilesList.getItems()) {
            if (s.replace("[DIR]", "").equals(fileName)) return true; //in case it is folder
        }
        return false;
    }

    public boolean fileAlreadyExistsOnClient(String fileName) {
        for (String s : clientFilesList.getItems()) {
            if (s.equals(fileName)) return true;
        }
        return false;
    }

    public void authorization(ActionEvent actionEvent) {
        String login = loginFieldLoginWindow.getText().trim();
        String password = passwordFieldLoginWindow.getText().trim();
        loginFieldLoginWindow.setFocusTraversable(true);
        if (!login.equals("") && !password.equals("")) {
            NettyClient.sendAuthInfo(login, password);
            passwordFieldLoginWindow.clear();
        }

    }

    public void registration(ActionEvent actionEvent) {
        String username = usernameFieldRegisterWindow.getText().trim();
        String login = loginFieldRegisterWindow.getText().trim();
        String password = passwordFieldRegisterWindow.getText().trim();
        if (!username.equals("") && !login.equals("") && !password.equals("")) {
            NettyClient.sendRegisterInfo(username, login, password);
            passwordFieldRegisterWindow.clear();
        }
    }

    public void showLoginWindow() {
        loginWindow.setVisible(true);
        registerWindow.setVisible(false);
        mainWindow.setVisible(false);
    }

    public void showRegisterWindow(ActionEvent actionEvent) {
        loginWindow.setVisible(false);
        registerWindow.setVisible(true);
        mainWindow.setVisible(false);
    }

    public void showMainWindow() {
        loginWindow.setVisible(false);
        registerWindow.setVisible(false);
        mainWindow.setVisible(true);
    }

    public void startNetworkService(Runnable runnable) {
        NettyClient.start(this, runnable);
    }
}
