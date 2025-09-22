package org.open.ftpopen;// ServidorController.java
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class ServidorController implements Initializable {
    @FXML private TextField txtPuerto;
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnSeleccionarCarpeta;
    @FXML private Label lblCarpetaServicio;
    @FXML private Button btnIniciar;
    @FXML private Button btnDetener;
    @FXML private TextArea txtLog;
    @FXML private Label lblEstado;

    private FtpServer servidor;
    private File carpetaServicio;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Valores por defecto
        txtPuerto.setText("21");
        txtUsuario.setText("admin");
        txtPassword.setText("admin123");

        // Carpeta por defecto
        carpetaServicio = new File(System.getProperty("user.home") + "/FTPServer");
        if (!carpetaServicio.exists()) {
            carpetaServicio.mkdirs();
        }
        lblCarpetaServicio.setText("Carpeta: " + carpetaServicio.getAbsolutePath());

        // Estados iniciales
        btnDetener.setDisable(true);
        lblEstado.setText("Estado: Detenido");
        lblEstado.setStyle("-fx-text-fill: red;");
    }

    @FXML
    private void seleccionarCarpetaServicio() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Seleccionar carpeta del servidor FTP");
        directoryChooser.setInitialDirectory(carpetaServicio.getParentFile());

        File nuevaCarpeta = directoryChooser.showDialog(btnSeleccionarCarpeta.getScene().getWindow());
        if (nuevaCarpeta != null) {
            carpetaServicio = nuevaCarpeta;
            lblCarpetaServicio.setText("Carpeta: " + carpetaServicio.getAbsolutePath());
            txtLog.appendText("Carpeta del servidor cambiada a: " + carpetaServicio.getAbsolutePath() + "\n");
        }
    }

    @FXML
    private void iniciarServidor() {
        try {
            FtpServerFactory serverFactory = new FtpServerFactory();
            ListenerFactory factory = new ListenerFactory();

            int puerto = Integer.parseInt(txtPuerto.getText());
            factory.setPort(puerto);

            // Configurar el listener
            serverFactory.addListener("default", factory.createListener());

            // Crear usuario
            PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
            UserManager userManager = userManagerFactory.createUserManager();

            BaseUser user = new BaseUser();
            user.setName(txtUsuario.getText());
            user.setPassword(txtPassword.getText());
            user.setHomeDirectory(carpetaServicio.getAbsolutePath());

            List<Authority> authorities = new ArrayList<>();
            authorities.add(new WritePermission());
            user.setAuthorities(authorities);

            userManager.save(user);
            serverFactory.setUserManager(userManager);

            servidor = serverFactory.createServer();
            servidor.start();

            Platform.runLater(() -> {
                txtLog.appendText("=== SERVIDOR FTP INICIADO ===\n");
                txtLog.appendText("Puerto: " + puerto + "\n");
                txtLog.appendText("Usuario: " + txtUsuario.getText() + "\n");
                txtLog.appendText("Carpeta compartida: " + carpetaServicio.getAbsolutePath() + "\n");
                txtLog.appendText("\n=== DIRECCIONES DE CONEXIÓN ===\n");

                // Mostrar todas las IPs disponibles
                mostrarDireccionesIP(puerto);

                txtLog.appendText("\n=== INSTRUCCIONES ===\n");
                txtLog.appendText("• Para conectar desde esta PC: usa 'localhost' o '127.0.0.1'\n");
                txtLog.appendText("• Para conectar desde otra PC: usa la IP de red mostrada arriba\n");
                txtLog.appendText("• Asegúrate de que el firewall permita el puerto " + puerto + "\n");
                txtLog.appendText("================================\n\n");

                btnIniciar.setDisable(true);
                btnDetener.setDisable(false);
                lblEstado.setText("Estado: Ejecutándose en puerto " + puerto);
                lblEstado.setStyle("-fx-text-fill: green;");

                // Deshabilitar campos de configuración
                txtPuerto.setDisable(true);
                txtUsuario.setDisable(true);
                txtPassword.setDisable(true);
                btnSeleccionarCarpeta.setDisable(true);
            });

        } catch (Exception e) {
            Platform.runLater(() -> {
                txtLog.appendText("Error al iniciar servidor: " + e.getMessage() + "\n");
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("No se pudo iniciar el servidor");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    private void mostrarDireccionesIP(int puerto) {
        try {
            // IP local (localhost)
            txtLog.appendText("• Local: ftp://localhost:" + puerto + "\n");
            txtLog.appendText("• Local: ftp://127.0.0.1:" + puerto + "\n");

            // Obtener todas las interfaces de red
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            boolean foundNetworkIP = false;

            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                // Saltar interfaces inactivas o loopback
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    // Solo mostrar IPv4 y no locales
                    if (!address.isLoopbackAddress() &&
                            !address.isLinkLocalAddress() &&
                            address.getAddress().length == 4) {

                        String ip = address.getHostAddress();
                        txtLog.appendText("• Red (" + networkInterface.getDisplayName() + "): ftp://" + ip + ":" + puerto + "\n");
                        foundNetworkIP = true;
                    }
                }
            }

            if (!foundNetworkIP) {
                txtLog.appendText("• No se encontraron direcciones de red activas\n");
                txtLog.appendText("• Verifica tu conexión a internet/red local\n");
            }

        } catch (SocketException e) {
            txtLog.appendText("• Error al obtener direcciones IP: " + e.getMessage() + "\n");
        }
    }

    @FXML
    private void detenerServidor() {
        if (servidor != null && !servidor.isStopped()) {
            servidor.stop();
            txtLog.appendText("=== SERVIDOR FTP DETENIDO ===\n");
            txtLog.appendText("El servidor ya no acepta conexiones\n\n");

            btnIniciar.setDisable(false);
            btnDetener.setDisable(true);
            lblEstado.setText("Estado: Detenido");
            lblEstado.setStyle("-fx-text-fill: red;");

            // Habilitar campos de configuración
            txtPuerto.setDisable(false);
            txtUsuario.setDisable(false);
            txtPassword.setDisable(false);
            btnSeleccionarCarpeta.setDisable(false);
        }
    }
}