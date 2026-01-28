package com.tunneler.ui;

import com.tunneler.router.OperationalMode;
import com.tunneler.router.RouterConfig;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Raw Mode panel (Tab 3)
 * Allows configuration of legacy direct pipe mode
 */
public class RawModePanel extends VBox {

    private RouterConfig config;

    public RawModePanel(RouterConfig config) {
        this.config = config;
        setPadding(new Insets(20));
        setSpacing(20);

        // Info section
        VBox infoBox = new VBox(10);
        infoBox.getStyleClass().add("settings-section");
        infoBox.setPadding(new Insets(20));

        Label title = new Label("⚡ Raw Mode (Legacy Pipe)");
        title.getStyleClass().add("section-title");

        Label description = new Label(
                "In Raw Mode, all traffic is forwarded directly without HTTP inspection or routing. " +
                        "This mode maintains 100% performance of the original tunneling functionality.");
        description.setWrapText(true);
        description.getStyleClass().add("description-text");

        infoBox.getChildren().addAll(title, description);

        // Configuration section
        VBox configBox = new VBox(10);
        configBox.getStyleClass().add("settings-section");
        configBox.setPadding(new Insets(20));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        Label hostLabel = new Label("Target Host:");
        TextField hostField = new TextField();
        hostField.textProperty().bindBidirectional(config.rawTargetHostProperty());
        hostField.setPromptText("e.g., 192.168.1.100");
        hostField.getStyleClass().add("form-input");

        Label portLabel = new Label("Target Port:");
        TextField portField = new TextField();
        portField.textProperty().bindBidirectional(config.rawTargetPortProperty(),
                new javafx.util.converter.NumberStringConverter());
        portField.setPromptText("e.g., 8080");
        portField.getStyleClass().add("form-input");

        grid.add(hostLabel, 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(portLabel, 0, 1);
        grid.add(portField, 1, 1);

        CheckBox enableCheckbox = new CheckBox("Enable Raw Mode (disable routing)");
        enableCheckbox.setSelected(config.getMode() == OperationalMode.RAW_MODE);
        enableCheckbox.selectedProperty().addListener((obs, old, newVal) -> {
            config.setMode(newVal ? OperationalMode.RAW_MODE : OperationalMode.ROUTING_MODE);
        });

        HBox buttonBox = new HBox(10);
        Button applyButton = new Button("Apply Raw Mode");
        applyButton.getStyleClass().addAll("btn", "btn-primary");
        applyButton.setOnAction(e -> {
            config.setMode(OperationalMode.RAW_MODE);
            enableCheckbox.setSelected(true);
            showAlert("Raw Mode Enabled", "All traffic will now be piped directly to " +
                    config.getRawTargetHost() + ":" + config.getRawTargetPort());
        });

        Button testButton = new Button("Test Connection");
        testButton.getStyleClass().add("btn");
        testButton.setOnAction(e -> testConnection());

        buttonBox.getChildren().addAll(applyButton, testButton);

        configBox.getChildren().addAll(grid, enableCheckbox, buttonBox);

        // Warning box
        VBox warningBox = new VBox(5);
        warningBox.getStyleClass().add("warning-box");
        warningBox.setPadding(new Insets(12));

        Label warningTitle = new Label("⚠️ Note:");
        warningTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffc107;");

        Label warningText = new Label(
                "When Raw Mode is enabled, all routing rules are ignored and traffic is piped directly to the configured target.");
        warningText.setWrapText(true);

        warningBox.getChildren().addAll(warningTitle, warningText);

        getChildren().addAll(infoBox, configBox, warningBox);
    }

    private void testConnection() {
        // TODO: Implement connection test
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Connection Test");
        alert.setHeaderText("Testing connection to " + config.getRawTargetHost() + ":" + config.getRawTargetPort());
        alert.setContentText("Connection test feature coming soon!");
        alert.showAndWait();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
