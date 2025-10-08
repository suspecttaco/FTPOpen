module org.open.ftpopen {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.commons.net;
    requires ftpserver.core;
    requires ftplet.api;
    requires atlantafx.base;


    opens org.open.ftpopen to javafx.fxml;
    exports org.open.ftpopen;
}