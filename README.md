// README.md
# Aplicación FTP Cliente-Servidor JavaFX

Una aplicación completa de cliente y servidor FTP desarrollada en Java con JavaFX, utilizando las librerías de Apache Commons Net y Apache FTP Server.

## Características

### Cliente FTP
- **Interfaz gráfica intuitiva**: No necesitas usar comandos, todo se maneja con clicks
- **Conexión configurable**: Host, puerto, usuario y contraseña personalizables
- **Gestión de archivos**: Subir, descargar, eliminar y listar archivos
- **Selector de carpeta de destino**: Elige dónde se descargan los archivos
- **Indicador de progreso**: Barra de progreso para operaciones de transferencia
- **Log de actividades**: Registro detallado de todas las operaciones

### Servidor FTP
- **Configuración simple**: Puerto, usuario y contraseña configurables
- **Protección con contraseña**: Solo usuarios autenticados pueden acceder
- **Carpeta compartida personalizable**: Selecciona qué carpeta compartir
- **Control de estado**: Iniciar/detener el servidor fácilmente
- **Log de servidor**: Monitoreo de la actividad del servidor

### Menú Principal
- **Opciones flexibles**: Abre solo el cliente, solo el servidor, o ambos
- **Interfaz limpia**: Menú principal simple para elegir el modo de uso

## Estructura del Proyecto

```
src/
├── main/
│   ├── java/
│   │   ├── MainApp.java
│   │   ├── MainController.java
│   │   ├── ClienteController.java
│   │   └── ServidorController.java
│   └── resources/
│       └── fxml/
│           ├── Main.fxml
│           ├── Cliente.fxml
│           └── Servidor.fxml
└── pom.xml
```

## Requisitos

- Java 11 o superior
- Maven (para gestión de dependencias)
- JavaFX Runtime (incluido en las dependencias)

## Dependencias

- **JavaFX Controls & FXML**: Para la interfaz gráfica
- **Apache Commons Net**: Para el cliente FTP
- **Apache FTP Server**: Para el servidor FTP

## Instalación y Uso

1. **Clonar/Descargar el proyecto**
2. **Compilar con Maven**:
   ```bash
   mvn clean compile
   ```
3. **Ejecutar la aplicación**:
   ```bash
   mvn javafx:run
   ```

## Uso de la Aplicación

### 1. Menú Principal
- Al iniciar, elige si quieres usar el cliente, servidor o ambos
- Cada opción abre una ventana independiente

### 2. Configurar el Servidor
- Establece puerto, usuario y contraseña
- Selecciona la carpeta que quieres compartir
- Haz clic en "Iniciar Servidor"

### 3. Usar el Cliente
- Configura la conexión (host: localhost si usas el servidor local)
- Ingresa las credenciales del servidor
- Selecciona la carpeta donde descargar archivos
- Conecta y gestiona archivos con los botones

## Características Técnicas

- **Programación asíncrona**: Las operaciones de red no bloquean la interfaz
- **Manejo de errores**: Alertas y logs informativos para errores
- **Modo pasivo**: El cliente usa modo pasivo FTP para mejor compatibilidad
- **Transferencia binaria**: Soporte para todo tipo de archivos
- **Interfaz responsiva**: Elementos se habilitan/deshabilitan según el estado

## Configuración por Defecto

- **Puerto del servidor**: 21
- **Usuario**: admin
- **Contraseña**: admin123
- **Carpeta del servidor**: ~/FTPServer (se crea automáticamente)
- **Carpeta de descarga**: ~/Downloads

Todas estas configuraciones se pueden cambiar desde la interfaz gráfica.

## Notas de Seguridad

- La contraseña se almacena temporalmente solo durante la sesión
- El servidor solo acepta conexiones autenticadas
- Se recomienda usar contraseñas seguras para el servidor