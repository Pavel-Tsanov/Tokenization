package Tokenization;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;


public class ClientSceneController {
    private ObjectOutputStream output;                //output stream to server
    private ObjectInputStream input;                  //input stream to server
    private Socket socket;                            //socket to communicate with server
    private Stage thisStage = new Stage();            //stage for displaying client's gui


    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private GridPane gridPane = new GridPane();

    @FXML
    private PasswordField pwdPassword;

    @FXML
    private TextField txtUsername;

    @FXML
    private Button btnRegister;

    @FXML
    private Button btnLogin;

    @FXML
    private Label lblTokenNumber;

    @FXML
    private Label lblCardNumber;

    @FXML
    private Button btnExtract;

    @FXML
    private TextField txtTokenNumber;

    @FXML
    private TextField txtCardNumber;

    //event handler when login button is clicked
    @FXML
    void btnLoginClicked(ActionEvent event) {
        String message;

        sendUserData("логин");          //send server typed user data to process login event

        try {
            message = (String) input.readObject();                                            //client receives result of login attempt
            messageDialog(message, "Логин", "Статус на влизането");     //popup window for the result
            if (message.equals("Успешно влизане!")) {
                //logged in users can now extract card/token numbers
                setTextFieldEditable(txtCardNumber, true);
                setTextFieldEditable(txtTokenNumber, true);
            }

        } catch (ClassNotFoundException | IOException classNotFoundException) {
            classNotFoundException.printStackTrace();
        }

    }


    //event handler when button for registration is clicked
    @FXML
    void btnRegisterClicked(ActionEvent event) throws IOException {
        sendUserData("регистрация");                    //send server typed user data to process registration event

        try {
            String message = (String) input.readObject();        //client receives result of registration attempt
            messageDialog(message, "Регистрация", "Статус на регистрацията");     //popup window for the result
        } catch (ClassNotFoundException classNotFoundException) {
            plainMessageDialog("Нема такъв клас", "Грешка");
        }

    }

    //event handler when client writes a card number
    @FXML
    void txtCardNumberOnKeyPressed(KeyEvent event) {
        String text = txtCardNumber.getText();

        if (text.equals("")) {
            //allow writing a token when there is no typed card number
            setTextFieldEditable(txtTokenNumber, true);
            btnExtract.setDisable(true);                         //there is nothing to extract if field is empty
        } else {
            //don't allow writing a token while typing a card number
            setTextFieldEditable(txtTokenNumber, false);
            btnExtract.setDisable(false);                       //extract token when done typing
        }
    }

    //event handler when client writes a token number
    @FXML
    void txtTokenNumberOnKeyPressed(KeyEvent event) {
        String text = txtTokenNumber.getText();

        if (text.length() > 0) {
            //allow writing a card number when there is no typed token number
            setTextFieldEditable(txtCardNumber, false);
            btnExtract.setDisable(false);                       //there is nothing to extract if field is empty
        } else {
            //don't allow writing a card number while typing a token
            setTextFieldEditable(txtCardNumber, true);
            btnExtract.setDisable(true);                         //extract card number when done typing
        }

    }

    //event handler when client clicks the extract button
    @FXML
    public void btnExtractClicked(ActionEvent actionEvent) {
        if (!txtTokenNumber.isEditable()) {
            try {
                //send to server clients wants to extract a token
                output.writeObject("извличане на токен");
                output.writeObject(txtCardNumber.getText());
                output.flush();

                //client receives result based on typed token
                String token = (String) input.readObject();

                //popup windows based on result
                if (token.equals("error"))
                    plainMessageDialog("Невалиден номер на банкова карта", "Регистрация на токен");

                else {
                    plainMessageDialog("Регистриран е токен " + token + " за карта " + txtCardNumber.getText(), "Регистрация на токен");
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else if (!txtCardNumber.isEditable()) {
            try {
                //send to server clients wants to extract a card number
                output.writeObject("извличане на карта");
                output.writeObject(txtTokenNumber.getText());
                output.flush();

                //client receives result based on typed card number
                String card = (String) input.readObject();

                //popup windows based on result
                if (card.equals("error"))
                    plainMessageDialog("Не е регистриран подобен токен", "Извличане на карта");
                else {
                    plainMessageDialog("Успешно е извлечен номер на карта " + card + " по токен " + txtTokenNumber.getText(), "Извличане на номер на карта");
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }



    @FXML
    void initialize() {
        //no logged user in the beginning
        //can't extract card/token numbers
        setTextFieldEditable(txtCardNumber, false);
        setTextFieldEditable(txtTokenNumber, false);
        btnExtract.setDisable(true);

        //close streams and socket when client closes window
        thisStage.setOnCloseRequest(windowEvent -> {
            closeConnection();
        });

    }

    //helper method for registration and login event handlers
    private void sendUserData(final String msgType) {
        XStream xStream = new XStream(new DomDriver());
        User user = new User(txtUsername.getText(), pwdPassword.getText());     //create new user based on typed information
        String xml = xStream.toXML(user);                                       //convert to xml

        //send event type and user to server
        try {
            output.writeObject(msgType);
            output.writeObject(xml);
            output.flush();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    //connect to server and process messages from server
    private void runClient() {
        try {
            connectToServer();                 //create a socket to make connection to server
            getStreams();                      //get input and output streams
        } catch (EOFException eofException) {
            eofException.printStackTrace();
        } catch (IOException ignored) {
        }
    }

    //close streams and socket
    private void closeConnection() {
        try {
            if (output != null) output.close();            //close output stream
            if (input != null) input.close();              //close input stream
            if (socket != null) socket.close();            //close socket
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }


    private void connectToServer() {
        plainMessageDialog("Опит за връзка...\n", "Връзка към сървъра");

        try {
            //create socket to connect to server
                socket = new Socket("127.0.0.1", 12345);

        } catch (IOException e) {
            plainMessageDialog(e.toString(), "Проблем с връзката");
            System.exit(0);
        }

        //inform client for successful connection
        plainMessageDialog("Свързан към: " + socket.getInetAddress().getHostName(), "Връзка към сървъра");
    }

    //get streams to send and receive data
    private void getStreams() throws IOException {
        output = new ObjectOutputStream(socket.getOutputStream());          //set up output stream for objects
        output.flush();                                                     //flush output buffer to send header information

        input = new ObjectInputStream(socket.getInputStream());             //set up input stream for objects
    }

    //helper method for setting text fields to be editable
    private void setTextFieldEditable(final TextField txtField, final boolean editable) {
        Platform.runLater(() -> {
            txtField.setEditable(editable);
        });
    }

    //popup window for client to see with title, header and information message
    public void messageDialog(String infoMessage, String titleBar, String headerMessage) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titleBar);
        alert.setContentText(infoMessage);
        alert.setHeaderText(headerMessage);
        alert.showAndWait();
    }

    //popup window for client to see with title and information message
    public void plainMessageDialog(String infoMessage, String titleBar) {
        messageDialog(infoMessage, titleBar, null);
    }

    //default constructor for a client
    public ClientSceneController() {
        try {
            //load fxml file for the gui
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ClientScene.fxml"));

            loader.setController(this);
            //set main stage
            thisStage.setScene(new Scene(loader.load()));
            thisStage.setTitle("Client");

            //separate thread for running the client
            Thread thread = new Thread(() -> {
                Platform.runLater(() -> {
                    thisStage.show();
                });
                Platform.runLater(this::runClient);
            });
            thread.start();

        } catch (IOException io) {
            io.printStackTrace();
        }
    }

}
