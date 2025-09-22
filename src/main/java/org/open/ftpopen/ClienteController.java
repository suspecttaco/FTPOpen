package org.open.ftpopen;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;

public class ClienteController implements Initializable {
    @FXML private TextField txtHost;
    @FXML private TextField txtPuerto;
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnConectar;
    @FXML private Button btnDesconectar;
    @FXML private ListView<String> listArchivos;
    @FXML private Button btnDescargar;
    @FXML private Button btnSubir;
    @FXML private Button btnEliminar;
    @FXML private Button btnSeleccionarCarpeta;
    @FXML private Label lblCarpetaDestino;
    @FXML private TextArea txtLog;
    @FXML private Label lblProgreso;

    private FTPClient ftpClient;
    private File carpetaDestino;
    private ObservableList<String> archivos = FXCollections.observableArrayList();

    // Buffer optimizado para transferencias
    private static final int BUFFER_SIZE = 8192; // 8KB buffer para balance entre memoria y rendimiento
    private static final long PROGRESS_UPDATE_INTERVAL = 50; // Actualizar progreso cada 50ms

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ftpClient = new FTPClient();
        listArchivos.setItems(archivos);

        // Valores por defecto
        txtHost.setText("localhost");
        txtPuerto.setText("21");
        txtUsuario.setText("admin");

        // Seleccionar carpeta de descarga por defecto
        carpetaDestino = new File(System.getProperty("user.home"));
        lblCarpetaDestino.setText("Destino: " + carpetaDestino.getAbsolutePath());

        // Estados iniciales
        btnDesconectar.setDisable(true);
        btnDescargar.setDisable(true);
        btnSubir.setDisable(true);
        btnEliminar.setDisable(true);

        // Inicializar label de progreso
        if (lblProgreso != null) {
            lblProgreso.setText("");
        }
    }

    @FXML
    private void conectar() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String host = txtHost.getText();
                int puerto = Integer.parseInt(txtPuerto.getText());
                String usuario = txtUsuario.getText();
                String password = txtPassword.getText();

                Platform.runLater(() -> {
                    txtLog.appendText("Conectando a " + host + ":" + puerto + "...\n");
                    if (lblProgreso != null) lblProgreso.setText("Conectando...");
                });

                ftpClient.connect(host, puerto);

                // Configurar timeouts para mejorar rendimiento
                ftpClient.setDefaultTimeout(30000);
                ftpClient.setConnectTimeout(30000);
                ftpClient.setDataTimeout(30000);

                boolean login = ftpClient.login(usuario, password);

                if (login) {
                    ftpClient.enterLocalPassiveMode();
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                    // Buffer más grande para mejorar rendimiento
                    ftpClient.setBufferSize(BUFFER_SIZE * 4); // 32KB para conexión

                    Platform.runLater(() -> {
                        txtLog.appendText("Conectado exitosamente\n");
                        btnConectar.setDisable(true);
                        btnDesconectar.setDisable(false);
                        btnDescargar.setDisable(false);
                        btnSubir.setDisable(false);
                        btnEliminar.setDisable(false);
                        if (lblProgreso != null) lblProgreso.setText("");
                        listarArchivos();
                    });
                } else {
                    throw new Exception("Error de autenticación");
                }

                return null;
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    txtLog.appendText("Error de conexión: " + getException().getMessage() + "\n");
                    if (lblProgreso != null) lblProgreso.setText("");
                });
            }
        };

        new Thread(task).start();
    }

    @FXML
    private void desconectar() {
        try {
            ftpClient.disconnect();
            txtLog.appendText("Desconectado\n");
            btnConectar.setDisable(false);
            btnDesconectar.setDisable(true);
            btnDescargar.setDisable(true);
            btnSubir.setDisable(true);
            btnEliminar.setDisable(true);
            archivos.clear();
            if (lblProgreso != null) lblProgreso.setText("");
        } catch (IOException e) {
            txtLog.appendText("Error al desconectar: " + e.getMessage() + "\n");
        }
    }

    private void listarArchivos() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                FTPFile[] files = ftpClient.listFiles();

                Platform.runLater(() -> {
                    archivos.clear();
                    for (FTPFile file : files) {
                        String tipo = file.isDirectory() ? "[DIR] " : "[FILE] ";
                        archivos.add(tipo + file.getName());
                    }
                    txtLog.appendText("Lista de archivos actualizada\n");
                });

                return null;
            }
        };

        new Thread(task).start();
    }

    @FXML
    private void descargarArchivo() {
        String seleccionado = listArchivos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            txtLog.appendText("Selecciona un archivo para descargar\n");
            return;
        }

        if (seleccionado.startsWith("[DIR]")) {
            txtLog.appendText("No se pueden descargar directorios\n");
            return;
        }

        String nombreArchivo = seleccionado.substring(7); // Remover "[FILE] "

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> {
                    txtLog.appendText("Descargando " + nombreArchivo + "...\n");
                    if (lblProgreso != null) lblProgreso.setText("Obteniendo información del archivo...");
                });

                // Obtener el tamaño del archivo
                FTPFile[] files = ftpClient.listFiles();
                long fileSize = 0;
                for (FTPFile file : files) {
                    if (file.getName().equals(nombreArchivo)) {
                        fileSize = file.getSize();
                        break;
                    }
                }

                final long totalSize = fileSize;
                File archivoLocal = new File(carpetaDestino, nombreArchivo);

                // Si el tamaño es desconocido, usar barra indeterminada
                if (totalSize <= 0) {
                    Platform.runLater(() -> {
                        if (lblProgreso != null) lblProgreso.setText("Descargando (tamaño desconocido)...");
                    });

                    try (FileOutputStream fos = new FileOutputStream(archivoLocal)) {
                        boolean success = ftpClient.retrieveFile(nombreArchivo, fos);
                        Platform.runLater(() -> {
                            if (success) {
                                txtLog.appendText("Descarga completada: " + archivoLocal.getAbsolutePath() + "\n");
                            } else {
                                txtLog.appendText("Error en la descarga\n");
                            }
                            if (lblProgreso != null) lblProgreso.setText("");
                        });
                    }
                } else {
                    // Descarga con progreso
                    downloadWithProgress(nombreArchivo, archivoLocal, totalSize);
                }

                return null;
            }
        };

        new Thread(task).start();
    }

    private void downloadWithProgress(String nombreArchivo, File archivoLocal, long totalSize) throws IOException {
        try (InputStream inputStream = ftpClient.retrieveFileStream(nombreArchivo);
             FileOutputStream outputStream = new FileOutputStream(archivoLocal);
             BufferedInputStream bis = new BufferedInputStream(inputStream, BUFFER_SIZE);
             BufferedOutputStream bos = new BufferedOutputStream(outputStream, BUFFER_SIZE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            long lastUpdateTime = 0;
            long startTime = System.currentTimeMillis();

            Platform.runLater(() -> {
                if (lblProgreso != null) {
                    lblProgreso.setText(String.format("Descargando: 0 / %s", formatFileSize(totalSize)));
                }
            });

            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // Actualizar progreso solo cada PROGRESS_UPDATE_INTERVAL ms para no saturar la UI
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL) {
                    final long finalTotalBytesRead = totalBytesRead;
                    final long currentTimeForLambda = currentTime;

                    Platform.runLater(() -> {
                        if (lblProgreso != null) {
                            long elapsedSeconds = (currentTimeForLambda - startTime) / 1000;
                            double speed = finalTotalBytesRead / (double) Math.max(1, elapsedSeconds); // bytes per second

                            lblProgreso.setText(String.format("Descargando: %s / %s (%s/s - %.1f%%)",
                                    formatFileSize(finalTotalBytesRead),
                                    formatFileSize(totalSize),
                                    formatFileSize((long) speed),
                                    (double) finalTotalBytesRead / totalSize * 100));
                        }
                    });

                    lastUpdateTime = currentTime;
                }
            }

            // Completar la transferencia FTP
            boolean success = ftpClient.completePendingCommand();

            Platform.runLater(() -> {
                if (success) {
                    txtLog.appendText("Descarga completada: " + archivoLocal.getAbsolutePath() + "\n");
                    if (lblProgreso != null) {
                        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                        lblProgreso.setText(String.format("Completado: %s en %ds",
                                formatFileSize(totalSize), elapsedSeconds));
                    }
                } else {
                    txtLog.appendText("Error en la descarga\n");
                    if (lblProgreso != null) lblProgreso.setText("Error en la descarga");
                }

                // Limpiar el estado después de 3 segundos
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        Platform.runLater(() -> {
                            if (lblProgreso != null) lblProgreso.setText("");
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
        }
    }

    @FXML
    private void subirArchivo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivo para subir");
        File archivo = fileChooser.showOpenDialog(btnSubir.getScene().getWindow());

        if (archivo != null) {
            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    long totalSize = archivo.length();

                    Platform.runLater(() -> {
                        txtLog.appendText("Subiendo " + archivo.getName() + "...\n");
                        if (lblProgreso != null) {
                            lblProgreso.setText(String.format("Subiendo: 0 / %s", formatFileSize(totalSize)));
                        }
                    });

                    uploadWithProgress(archivo, totalSize);
                    return null;
                }
            };

            new Thread(task).start();
        }
    }

    private void uploadWithProgress(File archivo, long totalSize) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(archivo);
             BufferedInputStream bis = new BufferedInputStream(inputStream, BUFFER_SIZE);
             OutputStream outputStream = ftpClient.storeFileStream(archivo.getName());
             BufferedOutputStream bos = new BufferedOutputStream(outputStream, BUFFER_SIZE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            long lastUpdateTime = 0;
            long startTime = System.currentTimeMillis();

            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // Actualizar progreso
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL) {
                    final long finalTotalBytesRead = totalBytesRead;
                    final long currentTimeForLambda = currentTime;

                    Platform.runLater(() -> {
                        if (lblProgreso != null) {
                            long elapsedSeconds = (currentTimeForLambda - startTime) / 1000;
                            double speed = finalTotalBytesRead / (double) Math.max(1, elapsedSeconds);

                            lblProgreso.setText(String.format("Subiendo: %s / %s (%s/s - %.1f%%)",
                                    formatFileSize(finalTotalBytesRead),
                                    formatFileSize(totalSize),
                                    formatFileSize((long) speed),
                                    (double) finalTotalBytesRead / totalSize * 100));
                        }
                    });

                    lastUpdateTime = currentTime;
                }
            }

            // Completar la transferencia FTP
            boolean success = ftpClient.completePendingCommand();

            Platform.runLater(() -> {
                if (success) {
                    txtLog.appendText("Subida completada: " + archivo.getName() + "\n");
                    if (lblProgreso != null) {
                        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                        lblProgreso.setText(String.format("Completado: %s en %ds",
                                formatFileSize(totalSize), elapsedSeconds));
                    }
                    listarArchivos();
                } else {
                    txtLog.appendText("Error en la subida\n");
                    if (lblProgreso != null) lblProgreso.setText("Error en la subida");
                }

                // Limpiar el estado después de 3 segundos
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        Platform.runLater(() -> {
                            if (lblProgreso != null) lblProgreso.setText("");
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @FXML
    private void eliminarArchivo() {
        String seleccionado = listArchivos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            txtLog.appendText("Selecciona un archivo para eliminar\n");
            return;
        }

        String nombreArchivo = seleccionado.substring(seleccionado.startsWith("[DIR]") ? 6 : 7);

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar eliminación");
        confirmacion.setHeaderText("¿Estás seguro?");
        confirmacion.setContentText("¿Deseas eliminar " + nombreArchivo + "?");

        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    boolean success;
                    if (seleccionado.startsWith("[DIR]")) {
                        success = ftpClient.removeDirectory(nombreArchivo);
                    } else {
                        success = ftpClient.deleteFile(nombreArchivo);
                    }

                    Platform.runLater(() -> {
                        if (success) {
                            txtLog.appendText("Eliminado: " + nombreArchivo + "\n");
                            listarArchivos();
                        } else {
                            txtLog.appendText("Error al eliminar " + nombreArchivo + "\n");
                        }
                    });

                    return null;
                }
            };

            new Thread(task).start();
        }
    }

    @FXML
    private void seleccionarCarpetaDestino() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Seleccionar carpeta de destino");
        directoryChooser.setInitialDirectory(carpetaDestino);

        File nuevaCarpeta = directoryChooser.showDialog(btnSeleccionarCarpeta.getScene().getWindow());
        if (nuevaCarpeta != null) {
            carpetaDestino = nuevaCarpeta;
            lblCarpetaDestino.setText("Destino: " + carpetaDestino.getAbsolutePath());
            txtLog.appendText("Carpeta de destino cambiada a: " + carpetaDestino.getAbsolutePath() + "\n");
        }
    }

    @FXML
    private void refrescarLista() {
        if (!btnDesconectar.isDisabled()) {
            listarArchivos();
        }
    }
}