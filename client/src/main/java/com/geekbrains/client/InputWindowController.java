package com.geekbrains.client;

import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class InputWindowController {

    public TextField inputWindowTextField;
    public Label inputWindowLabel;
    private Stage stage;
    private RunnableInputWindow runnableWindow;

    private boolean completed;


    public void setInputWindowLabel(String labelText) {
        inputWindowLabel.setText(labelText);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setRunnableInputWindow(RunnableInputWindow runnableWindow) {
        this.runnableWindow = runnableWindow;
    }

    public void confirmed(ActionEvent actionEvent) {
//        System.out.println("RUNNABLE");
        if (runnableWindow.run(inputWindowTextField)) {
            closeWindow(new ActionEvent());
        }
    }

    public Stage getStage() {
        return stage;
    }

    public void closeWindow(ActionEvent actionEvent) {
        stage.close();
    }
}
