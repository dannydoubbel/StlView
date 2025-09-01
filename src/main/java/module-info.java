module com.doebi.depthmapstl {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;

    opens com.doebi.depthmapstl to javafx.fxml;
    exports com.doebi.depthmapstl;
}