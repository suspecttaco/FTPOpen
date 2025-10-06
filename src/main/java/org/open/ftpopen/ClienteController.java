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
import java.util.Stack;

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
    @FXML private Label lblRutaActual;
    @FXML private Button btnRetroceder;

    private FTPClient ftpClient;
    private File carpetaDestino;
    private ObservableList<String> archivos = FXCollections.observableArrayList();
    private String rutaActual = "/";
    private Stack<String> historialRutas = new Stack<>();

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
        //BtnNavegacion
        btnRetroceder.setDisable(true);

        // Inicializar label de progreso
        if (lblProgreso != null) {
            lblProgreso.setText("");
        }

        // Inicializar label de ruta actual
        if (lblRutaActual != null) {
            lblRutaActual.setText("Ruta: /");
        }

        //Evento: Doble clic -> abrir carpeta
        listArchivos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                navegarACarpeta();
            }
        });
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
                ftpClient.setControlEncoding("UTF-8");

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

                        btnRetroceder.setDisable(false);

                        rutaActual = "/";
                        historialRutas.clear();
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
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
                txtLog.appendText("Desconectado\n");
                btnConectar.setDisable(false);
                btnDesconectar.setDisable(true);
                btnDescargar.setDisable(true);
                btnSubir.setDisable(true);
                btnEliminar.setDisable(true);

                btnRetroceder.setDisable(true);
                archivos.clear();
                rutaActual = "/";
                historialRutas.clear();

                archivos.clear();
                if (lblProgreso != null) lblProgreso.setText("");
                if (lblRutaActual != null) lblRutaActual.setText("Ruta: /");
            }

        } catch (IOException e) {
            txtLog.appendText("Error al desconectar: " + e.getMessage() + "\n");
        }
    }

    private void listarArchivos() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                FTPFile[] files = ftpClient.listFiles(rutaActual); // obtener path

                Platform.runLater(() -> {
                    archivos.clear();
                    for (FTPFile file : files) {
                        //ignorar . y ..
                        if (file.getName().equals(".") || file.getName().equals("..")) {
                            continue;
                        }
                        String tipo = file.isDirectory() ? "📁 " : "📄 "; // Cambiar los iconos
                        archivos.add(tipo + file.getName());
                    }
                    txtLog.appendText("Lista de archivos actualizada en: " + rutaActual + "\n"); // Actualizar mensaje
                    // Agregar estas líneas:
                    if (lblRutaActual != null) {
                        lblRutaActual.setText("Ruta: " + rutaActual);
                    }
                });

                return null;
            }
        };

        new Thread(task).start();
    }

    @FXML
    private void navegarACarpeta() {
        String seleccionado = listArchivos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }

        if (!seleccionado.startsWith("📁")) {
            txtLog.appendText("Selecciona una carpeta para navegar\n");
            return;
        }

        String nombreCarpeta = seleccionado.substring(2).trim();

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                historialRutas.push(rutaActual);

                if (rutaActual.equals("/")) {
                    rutaActual = "/" + nombreCarpeta;
                } else {
                    rutaActual = rutaActual + "/" + nombreCarpeta;
                }

                Platform.runLater(() -> {
                    listarArchivos();
                });

                return null;
            }
        };

        new Thread(task).start();
    }

    @FXML
    private void retroceder() {
        if (historialRutas.isEmpty()) {
            rutaActual = "/";
        } else {
            rutaActual = historialRutas.pop();
        }
        listarArchivos();
    }

    @FXML
    private void descargarArchivo() {
        String seleccionado = listArchivos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            txtLog.appendText("Selecciona un archivo o carpeta para descargar\n"); // Actualizar mensaje
            return;
        }

        // Cambiar la verificación:
        boolean esCarpeta = seleccionado.startsWith("📁");
        String nombre = seleccionado.substring(2).trim(); // Cambiar de substring(7) a substring(2)
        String rutaRemota = rutaActual.equals("/") ? "/" + nombre : rutaActual + "/" + nombre; // Nueva línea

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Agregar esta nueva lógica:
                if (esCarpeta) {
                    Platform.runLater(() -> {
                        txtLog.appendText("Descargando carpeta " + nombre + "...\n");
                        if (lblProgreso != null) lblProgreso.setText("Descargando carpeta...");
                    });

                    File carpetaLocal = new File(carpetaDestino, nombre);
                    descargarCarpetaRecursiva(rutaRemota, carpetaLocal);

                    Platform.runLater(() -> {
                        txtLog.appendText("Carpeta descargada completamente: " + carpetaLocal.getAbsolutePath() + "\n");
                        if (lblProgreso != null) lblProgreso.setText("");
                    });
                } else {
                    descargarArchivoSimple(nombre, rutaRemota);
                }

                return null;
            }
        };

        new Thread(task).start();
    }

    private void descargarArchivoSimple(String nombreArchivo, String rutaRemota) throws IOException {
        Platform.runLater(() -> {
            txtLog.appendText("Descargando " + nombreArchivo + "...\n");
            if (lblProgreso != null) lblProgreso.setText("Obteniendo información del archivo...");
        });

        // Obtener el tamaño del archivo
        FTPFile[] files = ftpClient.listFiles(rutaActual); // Usar rutaActual en lugar de listFiles() sin parámetros
        long fileSize = 0;
        for (FTPFile file : files) {
            if (file.getName().equals(nombreArchivo)) {
                fileSize = file.getSize();
                break;
            }
        }

        final long totalSize = fileSize;
        File archivoLocal = new File(carpetaDestino, nombreArchivo);

        if (totalSize <= 0) {
            Platform.runLater(() -> {
                if (lblProgreso != null) lblProgreso.setText("Descargando (tamaño desconocido)...");
            });

            try (FileOutputStream fos = new FileOutputStream(archivoLocal)) {
                boolean success = ftpClient.retrieveFile(rutaRemota, fos); // Usar rutaRemota en lugar de nombreArchivo
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
            downloadWithProgress(rutaRemota, archivoLocal, totalSize); // Usar rutaRemota
        }
    }

    private void descargarCarpetaRecursiva(String rutaRemota, File carpetaLocal) throws IOException {
        if (!carpetaLocal.exists()) {
            carpetaLocal.mkdirs();
        }

        FTPFile[] archivos = ftpClient.listFiles(rutaRemota);

        for (FTPFile archivo : archivos) {
            if (archivo.getName().equals(".") || archivo.getName().equals("..")) {
                continue;
            }

            String rutaRemotaArchivo = rutaRemota + "/" + archivo.getName();
            File archivoLocal = new File(carpetaLocal, archivo.getName());

            if (archivo.isDirectory()) {
                Platform.runLater(() -> {
                    txtLog.appendText("  Entrando a carpeta: " + archivo.getName() + "\n");
                });
                descargarCarpetaRecursiva(rutaRemotaArchivo, archivoLocal);
            } else {
                Platform.runLater(() -> {
                    txtLog.appendText("  Descargando: " + archivo.getName() + "\n");
                });

                try (FileOutputStream fos = new FileOutputStream(archivoLocal)) {
                    ftpClient.retrieveFile(rutaRemotaArchivo, fos);
                }
            }
        }
    }

    private void downloadWithProgress(String rutaRemota, File archivoLocal, long totalSize) throws IOException {
        try (InputStream inputStream = ftpClient.retrieveFileStream(rutaRemota);
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
        // Mostrar diálogo para elegir entre archivo o carpeta
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Seleccionar tipo de subida");
        alert.setHeaderText("¿Qué deseas subir?");
        alert.setContentText("Elige una opción:");

        ButtonType btnArchivo = new ButtonType("Archivo");
        ButtonType btnCarpeta = new ButtonType("Carpeta");
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnArchivo, btnCarpeta, btnCancelar);

        alert.showAndWait().ifPresent(tipo -> {
            if (tipo == btnArchivo) {
                subirArchivoSeleccionado();
            } else if (tipo == btnCarpeta) {
                subirCarpetaSeleccionada();
            }
        });
    }

    private void subirArchivoSeleccionado() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivo para subir");
        File archivo = fileChooser.showOpenDialog(btnSubir.getScene().getWindow());

        if (archivo != null) {
            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    long totalSize = archivo.length();
                    String rutaRemota = rutaActual.equals("/") ? "/" + archivo.getName() : rutaActual + "/" + archivo.getName();

                    Platform.runLater(() -> {
                        txtLog.appendText("Subiendo " + archivo.getName() + " a " + rutaActual + "...\n");
                        if (lblProgreso != null) {
                            lblProgreso.setText(String.format("Subiendo: 0 / %s", formatFileSize(totalSize)));
                        }
                    });

                    uploadWithProgress(archivo, rutaRemota, totalSize);

                    Platform.runLater(() -> {
                        listarArchivos();
                    });

                    return null;
                }
            };

            new Thread(task).start();
        }
    }

    private void subirCarpetaSeleccionada() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Seleccionar carpeta para subir");
        File carpeta = directoryChooser.showDialog(btnSubir.getScene().getWindow());

        if (carpeta != null) {
            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    Platform.runLater(() -> {
                        txtLog.appendText("Subiendo carpeta " + carpeta.getName() + " a " + rutaActual + "...\n");
                        if (lblProgreso != null) lblProgreso.setText("Subiendo carpeta...");
                    });

                    String rutaRemotaCarpeta = rutaActual.equals("/") ? "/" + carpeta.getName() : rutaActual + "/" + carpeta.getName();

                    subirCarpetaRecursiva(carpeta, rutaRemotaCarpeta);

                    Platform.runLater(() -> {
                        txtLog.appendText("Carpeta subida completamente: " + carpeta.getName() + "\n");
                        if (lblProgreso != null) lblProgreso.setText("");
                        listarArchivos();
                    });

                    return null;
                }
            };

            new Thread(task).start();
        }
    }

    private void subirCarpetaRecursiva(File carpetaLocal, String rutaRemota) throws IOException {
        // Crear carpeta en el servidor
        boolean created = ftpClient.makeDirectory(rutaRemota);
        if (!created) {
            Platform.runLater(() -> {
                txtLog.appendText("  La carpeta ya existe o no se pudo crear: " + rutaRemota + "\n");
            });
        }

        File[] archivos = carpetaLocal.listFiles();
        if (archivos == null) return;

        for (File archivo : archivos) {
            String rutaRemotaArchivo = rutaRemota + "/" + archivo.getName();

            if (archivo.isDirectory()) {
                Platform.runLater(() -> {
                    txtLog.appendText("  Creando carpeta: " + archivo.getName() + "\n");
                });
                subirCarpetaRecursiva(archivo, rutaRemotaArchivo);
            } else {
                Platform.runLater(() -> {
                    txtLog.appendText("  Subiendo: " + archivo.getName() + "\n");
                });

                try (FileInputStream fis = new FileInputStream(archivo);
                     OutputStream os = ftpClient.storeFileStream(rutaRemotaArchivo)) {

                    if (os == null) {
                        Platform.runLater(() -> {
                            txtLog.appendText("    Error: no se pudo abrir stream para " + archivo.getName() + "\n");
                        });
                        continue;
                    }

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }

                    os.flush();
                }

                // Completar el comando para que el archivo se guarde correctamente
                boolean success = ftpClient.completePendingCommand();
                if (!success) {
                    Platform.runLater(() -> {
                        txtLog.appendText("    Advertencia: posible error al subir " + archivo.getName() + "\n");
                    });
                }
            }
        }
    }

    private void uploadWithProgress(File archivo, String rutaRemota, long totalSize) throws IOException {
        OutputStream outputStream = null;
        FileInputStream inputStream = null;
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            outputStream = ftpClient.storeFileStream(rutaRemota);

            if (outputStream == null) {
                Platform.runLater(() -> {
                    txtLog.appendText("Error: No se pudo abrir el stream de subida. Código de respuesta: "
                            + ftpClient.getReplyCode() + "\n");
                    if (lblProgreso != null) lblProgreso.setText("Error al subir");
                });
                return;
            }

            inputStream = new FileInputStream(archivo);
            bis = new BufferedInputStream(inputStream, BUFFER_SIZE);
            bos = new BufferedOutputStream(outputStream, BUFFER_SIZE);

            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            long lastUpdateTime = 0;
            long startTime = System.currentTimeMillis();

            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

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

            bos.flush();

        } finally {
            // Cerrar streams en orden correcto
            if (bos != null) {
                try { bos.close(); } catch (IOException e) { }
            }
            if (bis != null) {
                try { bis.close(); } catch (IOException e) { }
            }
            if (inputStream != null) {
                try { inputStream.close(); } catch (IOException e) { }
            }
        }

        // Completar el comando DESPUÉS de cerrar los streams
        boolean success = ftpClient.completePendingCommand();

        Platform.runLater(() -> {
            if (success) {
                txtLog.appendText("Subida completada: " + archivo.getName() + "\n");
                if (lblProgreso != null) {
                    lblProgreso.setText("Completado");
                }
            } else {
                txtLog.appendText("Error en la subida. Código: " + ftpClient.getReplyCode() + "\n");
                if (lblProgreso != null) lblProgreso.setText("Error en la subida");
            }

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

        String nombreArchivo = seleccionado.substring(2).trim();
        String rutaRemota = rutaActual.equals("/") ? "/" + nombreArchivo : rutaActual + "/" + nombreArchivo;


        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar eliminación");
        confirmacion.setHeaderText("¿Estás seguro?");
        confirmacion.setContentText("¿Deseas eliminar " + nombreArchivo + "?");

        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    boolean success;
                    if (seleccionado.startsWith("📁")) {
                        success = ftpClient.removeDirectory(rutaRemota);
                    } else {
                        success = ftpClient.deleteFile(rutaRemota);
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