package Tokenization;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
 import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

//Server GUI
public class ServerJavaFX extends Application {

    private TextArea txtArea;                               //text area for displaying messages during processing of requests
    private Button btnExport;                               //button for exporting saved cards <-> token in file
    private ServerSocket server;                            //server socket
    private Socket socket;                                  //connection to client
    private List<String> tokenCards = new ArrayList<>();    //list containing all cards <-> token
    private List<String> users = new ArrayList<>();         //list containing all registered users
    private int counter = 1;                                //counter of connections


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox();
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(14));
        root.setSpacing(8);

        txtArea = new TextArea();
        txtArea.setEditable(false);                    //text area isn't for editing
        btnExport = new Button();
        btnExport.setText("Експорт");
        btnExport.setOnAction(event -> {              //execute export() function on click
            export();
        });

        root.getChildren().addAll(txtArea, btnExport);
        Scene scene = new Scene(root, 400, 300, Color.web("#666970"));


        primaryStage.setOnCloseRequest(evt -> stop());      //shutdown the application
        primaryStage.setScene(scene);
        primaryStage.setTitle("Server");
        primaryStage.show();
        Thread thread = new Thread(this::runServer);      //process networking in a separate Thread
        thread.start();
    }

    private void export(){
        XStream xStream = new XStream(new DomDriver());

        //try-with-resources block for exporting all saved card <-> tokens
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("tokenCards.txt"))) {
            writer.write("Card number \t <-> \t token number\n");

            tokenCards
                    .stream()
                    .map(xStream::fromXML)                            //convert from xml to plain string
                    .sorted(new Comparator<Object>() {                //sort on card numbers
                        @Override
                        public int compare(Object o1, Object o2) {
                            String s1 = (String) o1;
                            String s2 = (String) o2;

                            return s1.compareTo(s2);
                        }
                    })
                    //write every card <-> token in file
                    .forEach(card -> {
                        try {
                            writer.write(((String) card).substring(0,16) + " <-> " + ((String) card).substring(16) + '\n');
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //class for processing requests
    private class ServerWorker implements Runnable {
        private Socket socket;
        private ObjectInputStream input;
        private ObjectOutputStream output;

        @Override
        public void run() {
            try {
                counter++;
                getStreams();

                //process client's requests until connection is closed
                while (true)
                    processConnection();

            } catch (ClassNotFoundException | IOException eofException) {
                eofException.printStackTrace();
            }finally{
                closeConnection();
            }
        }

        public ServerWorker(Socket socket) {
            this.socket = socket;
        }

        //processing client's request
        private void processConnection() throws EOFException, IOException, ClassNotFoundException {
            String event = (String) input.readObject();                 //server receives client's type of request
            XStream xStream = new XStream(new DomDriver());

            displayMessage("Обработка на " + event);     //display request type in text area

            //request type is registration or login
            if (event.equals("регистрация") || event.equals("логин")) {
                User user = (User) xStream.fromXML((String) input.readObject());
                boolean existsUser = false, existsPassword = false;

                //search through registered users for a match
                for (String currUserString : users) {
                    User currUser = (User) xStream.fromXML(currUserString);

                    if (currUser.getUsername().equals(user.getUsername())) {
                        existsUser = true;
                        if (currUser.getPassword().equals(user.getPassword()))
                            existsPassword = true;
                        break;
                    }
                }

                //send client result of the request
                if (event.equals("логин")) {
                    if (existsUser && existsPassword) {                  //client typed correct username and password
                        output.writeObject("Успешно влизане!");
                    } else {                                             //client type incorrect username or password
                        output.writeObject("Невалидно име или парола!");
                    }
                } else {

                    if (existsUser) {                                    //client tried to register with already taken username
                        output.writeObject("Съществува потребител с такова име!");
                    } else {
                        users.add(xStream.toXML(user));                 //add newly registered user to the list of users
                        output.writeObject("Регистрацията на потребител " + user.getUsername() + " е успешна!");
                    }
                }

                output.flush();                                         //empty output buffer
            }
            //token extraction by card number
            else if (event.equals("извличане на токен")) {
                String cardNumber = (String) input.readObject();

                if (!validateCardNumber(cardNumber)) {                  //check for valid bank card number received
                    output.writeObject("error");
                    displayMessage("Провал");
                } else {
                    String token;

                    do {
                        //generate token
                        token = tokenizeCardNumber(cardNumber);
                    } while (tokenCards.contains(xStream.toXML(cardNumber + token)));  //loop in case generated token exists already

                    displayMessage("Записано " + cardNumber + " <-> " + token);
                    tokenCards.add(xStream.toXML(cardNumber + token));                //add new token

                    output.writeObject(token);                                           //send new token to client
                    output.flush();
                }
            }
            //card number extraction by token
            else if (event.equals("извличане на карта")) {
                String tokenNumber = (String) input.readObject();
                String card = findCard(tokenNumber);                     //extract token by the card number
                displayMessage("Взета карта " + card + "по токен " + tokenNumber);

                if (card.equals("")) {                //given token is not registered
                    output.writeObject("error");
                    displayMessage("Провал");
                } else {                             //found bank card number
                    output.writeObject(card);
                    output.flush();
                }
            }

        }

        //close streams and socket
        private void closeConnection() {
            displayMessage("\nПриключване на връзката");

            try {
                if (output != null) output.close();
                if (input != null) input.close();
                if (socket != null) socket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        //get streams to send and receive data
        private void getStreams() throws IOException {
            output = new ObjectOutputStream(socket.getOutputStream());          //set up output stream for objects
            output.flush();                                                     //flush output buffer

            input = new ObjectInputStream(socket.getInputStream());             //set up input stream for objects
        }
    }

    //set up and run server
    private void runServer() {
        try {
            server = new ServerSocket(12345, 100);       //create server socket on port 12345

            try {
                while(true) {
                    waitForConnection();                              //wait for a connection from a client
                    new Thread(new ServerWorker(socket)).start();     //process client in another thread
                }


            } catch (EOFException eofException) {
                eofException.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //display a message in the text area
    private void displayMessage(final String messageToDisplay) {
        Platform.runLater(() -> txtArea.appendText(messageToDisplay + '\n'));
    }

    //wait for a connection to arrive, then display connection info
    private void waitForConnection() throws IOException {
        displayMessage("Изчакване за връзка");
        try {
            socket = server.accept();
        } catch (IOException e) {
            throw new RuntimeException("Проблем със свързването.", e);
        }
        displayMessage("Връзка " + counter + " получена от: " + socket.getInetAddress().getHostAddress() + '\n');
    }


    //search for a card number based on a token
    private String findCard(String token) {
        XStream xStream = new XStream(new DomDriver());

        for (String tokenCard : tokenCards) {
            String currToken = (String) xStream.fromXML(tokenCard);        //currToken is String with size 32 - first 16 chars are card number
            if (currToken.substring(16).equals(token))                     //                                   other 16 are token number
                return currToken.substring(0, 16);
        }
        return "";                                                         //couldn't find such a token
    }

    //check for a valid card number
    private boolean validateCardNumber(String cardNumber) {
        if (Pattern.matches("[3|4|5|6]\\d{15}", cardNumber)) {     //card numbers must have 16 digits starting with 3,4,5 or 6
            int sum = 0;


            //check number by Luhn formula
            for (int i = 0; i < cardNumber.length() - 2; i++) {
                int digit = Integer.parseInt(String.valueOf(cardNumber.charAt(i)));

                if (i % 2 == 0)
                    digit *= 2;

                if (digit > 9)
                    digit -= 9;

                sum += digit;
            }

            return sum % 10 == 0;
        }
        return false;
    }

    //generate token of a card number
    private String tokenizeCardNumber(String cardNumber) {
        char[] cardInArray = cardNumber.toCharArray();
        char[] tokenInArray = new char[16];
        Random random = new Random();


        for (int i = 0; i < 12; i++) {
            int digit;

            do {
                digit = random.nextInt(10);               //digits of token are randomly generated

                while (i == 0 && digit >= 3 && digit <= 6)       //first digit of token must not be 3,4,5 or 6
                    digit = random.nextInt(10);

                tokenInArray[i] = Character.forDigit(digit, 10);

            } while (Character.getNumericValue(cardInArray[i]) == digit);      //generated digit mustn't be the same as the card's digit on this position
        }

        //last 4 digits of token and card are the same
        System.arraycopy(cardInArray, 12, tokenInArray, 12, 4);

        int sum = 0;
        for (char c : tokenInArray)
            sum += Character.getNumericValue(c);

        if (sum % 10 == 0)                               //valid token's sum of digits shouldn't be divisible by 10
            return tokenizeCardNumber(cardNumber);       //generate new token

        return String.valueOf(tokenInArray);             //return string representation of the token in char array
    }


    public void stop() {
        Platform.exit();
        System.exit(0);
    }

}
