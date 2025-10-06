package org.open.ftpopen;// MainController.java
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class MainController {
    @FXML private Button btnCliente;
    @FXML private Button btnServidor;
    @FXML private Button btnAmbos;

    @FXML
    private void abrirCliente() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Cliente.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Cliente FTP");
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void abrirServidor() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Servidor.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Servidor FTP");
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void abrirAmbos() {
        abrirCliente();
        abrirServidor();
    }
}