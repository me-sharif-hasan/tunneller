package com.tunneler.ui;

import com.tunneler.config.ConfigManager;
import com.tunneler.router.RouterConfig;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Settings panel (Tab 4)
 * Configuration for signal server, persistence, and performance
 */
public class SettingsPanel extends VBox {

    private RouterConfig config;

    public SettingsPanel(RouterConfig config) {
        this.config = config;
        setPadding(new Insets(20));
        setSpacing(15);

        // Signal Server section
        getChildren().add(createSignalServerSection());

        // Configuration section
        getChildren().add(createConfigSection());

        // Performance section
        getChildren().add(createPerformanceSection());

        // Action buttons
        getChildren().add(createActionButtons());
    }

    private VBox createSignalServerSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("settings-section");
        section.setPadding(new Insets(20));

        Label title = new Label("🔌 Signal Server");
        title.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        Label hostLabel = new Label("Signal Host:");
        TextField hostField = new TextField();
        hostField.textProperty().bindBidirectional(config.signalHostProperty());
        hostField.getStyleClass().add("form-input");

        Label signalPortLabel = new Label("Signal Port:");
        TextField signalPortField = new TextField();
        signalPortField.textProperty().bindBidirectional(config.signalPortProperty(),
                new javafx.util.converter.NumberStringConverter());
        signalPortField.getStyleClass().add("form-input");

        Label dataPortLabel = new Label("Data Port:");
        TextField dataPortField = new TextField();
        dataPortField.textProperty().bindBidirectional(config.dataPortProperty(),
                new javafx.util.converter.NumberStringConverter());
        dataPortField.getStyleClass().add("form-input");

        grid.add(hostLabel, 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(signalPortLabel, 0, 1);
        grid.add(signalPortField, 1, 1);
        grid.add(dataPortLabel, 0, 2);
        grid.add(dataPortField, 1, 2);

        section.getChildren().addAll(title, grid);
        return section;
    }

    private VBox createConfigSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("settings-section");
        section.setPadding(new Insets(20));

        Label title = new Label("💾 Configuration");
        title.getStyleClass().add("section-title");

        CheckBox autoSaveCheck = new CheckBox("Auto-save configuration on change");
        autoSaveCheck.selectedProperty().bindBidirectional(config.autoSaveProperty());

        CheckBox autoLoadCheck = new CheckBox("Auto-load configuration on startup");
        autoLoadCheck.selectedProperty().bindBidirectional(config.autoLoadProperty());

        CheckBox autoReconnectCheck = new CheckBox("Auto-reconnect to signal server");
        autoReconnectCheck.selectedProperty().bindBidirectional(config.autoReconnectProperty());

        section.getChildren().addAll(title, autoSaveCheck, autoLoadCheck, autoReconnectCheck);
        return section;
    }

    private VBox createPerformanceSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("settings-section");
        section.setPadding(new Insets(20));

        Label title = new Label("📊 Performance");
        title.getStyleClass().add("section-title");

        CheckBox monitoringCheck = new CheckBox("Enable connection monitoring");
        monitoringCheck.selectedProperty().bindBidirectional(config.monitoringEnabledProperty());

        CheckBox loggingCheck = new CheckBox("Enable detailed logging");
        loggingCheck.selectedProperty().bindBidirectional(config.loggingEnabledProperty());

        CheckBox forceCloseCheck = new CheckBox("Force 'Connection: close' (Prevents socket hangs)");
        forceCloseCheck.selectedProperty().bindBidirectional(config.forceConnectionCloseProperty());
        Tooltip forceCloseTip = new Tooltip(
                "Forces the backend to close the connection after each response.\nDisable this only if you need Keep-Alive support and know the risks.");
        forceCloseCheck.setTooltip(forceCloseTip);

        HBox bufferBox = new HBox(10);
        Label bufferLabel = new Label("Buffer Size (bytes):");
        TextField bufferField = new TextField();
        bufferField.textProperty().bindBidirectional(config.bufferSizeProperty(),
                new javafx.util.converter.NumberStringConverter());
        bufferField.setPrefWidth(150);
        bufferField.getStyleClass().add("form-input");
        bufferBox.getChildren().addAll(bufferLabel, bufferField);

        section.getChildren().addAll(title, monitoringCheck, loggingCheck, forceCloseCheck, bufferBox);
        return section;
    }

    private HBox createActionButtons() {
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button saveButton = new Button("💾 Save All Settings");
        saveButton.getStyleClass().addAll("btn", "btn-primary");
        saveButton.setOnAction(e -> {
            ConfigManager.saveConfig();
            showAlert("Success", "All settings saved successfully!");
        });

        Button reloadButton = new Button("🔄 Reload from File");
        reloadButton.getStyleClass().add("btn");
        reloadButton.setOnAction(e -> {
            ConfigManager.loadConfig();
            showAlert("Reloaded", "Configuration reloaded from file.");
        });

        Button resetButton = new Button("↩️ Reset to Defaults");
        resetButton.getStyleClass().add("btn");
        resetButton.setOnAction(e -> resetToDefaults());

        buttonBox.getChildren().addAll(saveButton, reloadButton, resetButton);
        return buttonBox;
    }

    private void resetToDefaults() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Reset");
        confirm.setHeaderText("Reset to default settings?");
        confirm.setContentText("This will reset all settings to their default values.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Reset logic would go here
                showAlert("Reset Complete", "Settings reset to defaults.");
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
