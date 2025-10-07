package org.open.ftpopen;// ServidorController.java
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.*;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Enumeration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            logEvent("Carpeta cambiada a: " + carpetaServicio.getAbsolutePath());
        }
    }

    @FXML
    private void iniciarServidor() {
        try {
            // Limpiar el log al iniciar el servidor
            txtLog.clear();
            
            int puerto = Integer.parseInt(txtPuerto.getText());
            
            // Intentar configurar el firewall automáticamente
            configurarFirewall(puerto);
            
            FtpServerFactory serverFactory = new FtpServerFactory();
            ListenerFactory factory = new ListenerFactory();

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

            // Agregar el Ftplet para monitorear eventos
            Map<String, Ftplet> ftplets = new HashMap<>();
            ftplets.put("eventLogger", new EventLoggerFtplet());
            serverFactory.setFtplets(ftplets);

            servidor = serverFactory.createServer();
            servidor.start();

            Platform.runLater(() -> {
                logEvent("SERVIDOR INICIADO");
                logEvent("Puerto: " + puerto);
                logEvent("Usuario: " + txtUsuario.getText());
                logEvent("Carpeta: " + carpetaServicio.getAbsolutePath());
                
                // Mostrar solo IP de red local
                mostrarIPRedLocal(puerto);
                txtLog.appendText("\n");

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
                logEvent("ERROR: " + e.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("No se pudo iniciar el servidor");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    private void mostrarIPRedLocal(int puerto) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String ipPrincipal = null;
            int prioridad = 0;

            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                String nombreInterfaz = networkInterface.getDisplayName().toLowerCase();
                String nombreCorto = networkInterface.getName().toLowerCase();

                // Filtrar adaptadores virtuales y no deseados
                if (esAdaptadorVirtual(nombreInterfaz, nombreCorto)) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    if (!address.isLoopbackAddress() &&
                            !address.isLinkLocalAddress() &&
                            address.getAddress().length == 4) {

                        String ip = address.getHostAddress();
                        
                        // Determinar prioridad: Ethernet > WiFi > Otros
                        int prioridadActual = obtenerPrioridad(nombreInterfaz, nombreCorto);
                        
                        if (prioridadActual > prioridad) {
                            prioridad = prioridadActual;
                            ipPrincipal = ip;
                        }
                    }
                }
            }

            if (ipPrincipal != null) {
                logEvent("IP Red Local: " + ipPrincipal + ":" + puerto);
            } else {
                logEvent("No se encontró IP de red activa");
            }

        } catch (SocketException e) {
            logEvent("Error al obtener IP: " + e.getMessage());
        }
    }

    private boolean esAdaptadorVirtual(String displayName, String name) {
        String[] palabrasClave = {
            "virtual", "vmware", "vbox", "virtualbox", "hyper-v", "vethernet",
            "docker", "wsl", "vpn", "tap", "tun", "loopback", "pseudo",
            "bridge", "vnic", "npcap", "wireshark", "bluetooth"
        };

        String textoCompleto = (displayName + " " + name).toLowerCase();
        
        for (String palabra : palabrasClave) {
            if (textoCompleto.contains(palabra)) {
                return true;
            }
        }
        
        return false;
    }

    private int obtenerPrioridad(String displayName, String name) {
        String textoCompleto = (displayName + " " + name).toLowerCase();
        
        // Ethernet tiene máxima prioridad
        if (textoCompleto.contains("ethernet") || 
            textoCompleto.contains("eth") || 
            textoCompleto.contains("realtek") ||
            textoCompleto.contains("intel") && textoCompleto.contains("gigabit")) {
            return 3;
        }
        
        // WiFi/WLAN tiene segunda prioridad
        if (textoCompleto.contains("wi-fi") || 
            textoCompleto.contains("wifi") || 
            textoCompleto.contains("wlan") ||
            textoCompleto.contains("wireless") ||
            textoCompleto.contains("802.11")) {
            return 2;
        }
        
        // Otros adaptadores físicos
        return 1;
    }

    @FXML
    private void detenerServidor() {
        if (servidor != null && !servidor.isStopped()) {
            servidor.stop();
            logEvent("SERVIDOR DETENIDO\n");

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

    private void logEvent(String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        txtLog.appendText("[" + timestamp + "] " + message + "\n");
    }

    private void configurarFirewall(int puerto) {
        try {
            // Obtener la ruta del ejecutable Java actual
            String javaPath = System.getProperty("java.home") + "\\bin\\javaw.exe";
            
            // Nombre de la regla
            String nombreRegla = "FTPOpen_Server_Puerto_" + puerto;
            
            // Comando para verificar si la regla ya existe
            ProcessBuilder checkBuilder = new ProcessBuilder(
                "netsh", "advfirewall", "firewall", "show", "rule", "name=" + nombreRegla
            );
            Process checkProcess = checkBuilder.start();
            checkProcess.waitFor();
            
            // Si la regla no existe (código de salida != 0), intentar crearla
            if (checkProcess.exitValue() != 0) {
                logEvent("Configurando regla de firewall...");
                
                // Crear la regla con permisos elevados usando UAC
                boolean exito = crearReglaFirewallConUAC(nombreRegla, puerto, javaPath);
                
                if (exito) {
                    logEvent("✓ Regla de firewall creada correctamente");
                } else {
                    logEvent("⚠ No se pudo crear la regla de firewall");
                    logEvent("⚠ El servidor funcionará solo para conexiones locales");
                }
            } else {
                logEvent("✓ Regla de firewall ya existe");
            }
            
        } catch (Exception e) {
            logEvent("⚠ Error al verificar firewall: " + e.getMessage());
        }
    }

    private boolean crearReglaFirewallConUAC(String nombreRegla, int puerto, String javaPath) {
        try {
            // Crear un script temporal de PowerShell
            File tempScript = File.createTempFile("ftp_firewall", ".ps1");
            tempScript.deleteOnExit();
            
            // Escribir el comando de PowerShell que crea la regla del firewall
            try (java.io.PrintWriter writer = new java.io.PrintWriter(tempScript)) {
                writer.println("# Script para crear regla de firewall");
                writer.println("$ruleName = '" + nombreRegla + "'");
                writer.println("$port = " + puerto);
                writer.println("$javaPath = '" + javaPath.replace("\\", "\\\\") + "'");
                writer.println();
                writer.println("try {");
                writer.println("    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP -LocalPort $port -Program $javaPath -Enabled True -ErrorAction Stop");
                writer.println("    Write-Host 'Regla de firewall creada exitosamente'");
                writer.println("    exit 0");
                writer.println("} catch {");
                writer.println("    Write-Host 'Error al crear regla: ' $_");
                writer.println("    exit 1");
                writer.println("}");
            }
            
            // Crear archivo VBS que ejecutará PowerShell con permisos elevados
            File tempVbs = File.createTempFile("elevate_ftp", ".vbs");
            tempVbs.deleteOnExit();
            
            try (java.io.PrintWriter writer = new java.io.PrintWriter(tempVbs)) {
                writer.println("Set UAC = CreateObject(\"Shell.Application\")");
                writer.println("UAC.ShellExecute \"powershell.exe\", \"-ExecutionPolicy Bypass -WindowStyle Hidden -File \"\"" + 
                    tempScript.getAbsolutePath() + "\"\"\", \"\", \"runas\", 0");
            }
            
            // Ejecutar el script VBS (esto mostrará el UAC prompt)
            Process process = Runtime.getRuntime().exec("wscript \"" + tempVbs.getAbsolutePath() + "\"");
            
            // Esperar a que el usuario responda al UAC y se ejecute el comando
            Thread.sleep(3000);
            
            // Verificar si la regla fue creada exitosamente
            ProcessBuilder checkBuilder = new ProcessBuilder(
                "netsh", "advfirewall", "firewall", "show", "rule", "name=" + nombreRegla
            );
            Process checkProcess = checkBuilder.start();
            checkProcess.waitFor();
            
            return checkProcess.exitValue() == 0;
            
        } catch (Exception e) {
            logEvent("Error al solicitar permisos UAC: " + e.getMessage());
            return false;
        }
    }

    private void mostrarDialogoFirewallManual(int puerto) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Firewall no configurado");
            alert.setHeaderText("La regla de firewall no pudo ser creada");
            alert.setContentText(
                "El servidor está funcionando, pero solo será accesible desde este equipo.\n\n" +
                "Para permitir conexiones desde otros dispositivos:\n" +
                "1. Cierra y vuelve a iniciar el servidor\n" +
                "2. Acepta el mensaje de UAC cuando aparezca\n\n" +
                "Nota: El servidor funciona correctamente para conexiones locales."
            );
            alert.showAndWait();
        });
    }

    // Clase interna para monitorear eventos del servidor FTP
    private class EventLoggerFtplet extends DefaultFtplet {
        
        @Override
        public FtpletResult onLogin(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String ip = session.getClientAddress().getAddress().getHostAddress();
                logEvent("LOGIN: Usuario '" + user + "' desde " + ip);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onDisconnect(FtpSession session) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser() != null ? session.getUser().getName() : "Desconocido";
                String ip = session.getClientAddress().getAddress().getHostAddress();
                logEvent("LOGOUT: Usuario '" + user + "' desde " + ip);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onUploadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String archivo = request.getArgument();
                logEvent("SUBIDA INICIADA: '" + archivo + "' por " + user);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String archivo = request.getArgument();
                logEvent("SUBIDA COMPLETADA: '" + archivo + "' por " + user);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onDownloadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String archivo = request.getArgument();
                logEvent("DESCARGA INICIADA: '" + archivo + "' por " + user);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onDownloadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String archivo = request.getArgument();
                logEvent("DESCARGA COMPLETADA: '" + archivo + "' por " + user);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onDeleteStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String archivo = request.getArgument();
                logEvent("ELIMINADO: '" + archivo + "' por " + user);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onRenameStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String archivoOrigen = request.getArgument();
                logEvent("RENOMBRADO/MOVIDO: '" + archivoOrigen + "' por " + user);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onMkdirStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String carpeta = request.getArgument();
                logEvent("CARPETA CREADA: '" + carpeta + "' por " + user);
            });
            return FtpletResult.DEFAULT;
        }

        @Override
        public FtpletResult onRmdirStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
            Platform.runLater(() -> {
                String user = session.getUser().getName();
                String carpeta = request.getArgument();
                logEvent("CARPETA ELIMINADA: '" + carpeta + "' por " + user);
            });
            return FtpletResult.DEFAULT;
        }
    }
}