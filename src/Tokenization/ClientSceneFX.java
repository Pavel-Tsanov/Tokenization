package Tokenization;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.stage.Stage;

public class ClientSceneFX extends Application {

    public static void main(String[] args) { launch(args); }


    //test server's concurrent work with multiple clients
    @Override
    public void start(Stage primaryStage) {
        for(int i=0;i<5;i++) {
            new ClientSceneController();
        }

    }
}
