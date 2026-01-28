package com.tunneler;

import com.tunneler.router.RouterConfig;
import com.tunneler.config.ConfigManager;
import com.tunneler.ui.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Main JavaFX application entry point
 */
public class App extends Application {

    private RouterConfig config;
    private TunnelClient tunnelClient;
    private boolean isConnected = false;

    @Override
    public void start(Stage primaryStage) {
        config = RouterConfig.getInstance();

        // Create main layout
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // Header
        HBox header = createHeader();
        root.setTop(header);

        // Tab pane
        TabPane tabPane = createTabs();
        root.setCenter(tabPane);

        // Create scene with dark theme
        Scene scene = new Scene(root, 1200, 700);
        scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());

        primaryStage.setTitle("Tunneler - Dynamic HTTP Router");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Save config on close
        primaryStage.setOnCloseRequest(e -> {
            if (config.isAutoSave()) {
                ConfigManager.saveConfig();
            }
            System.exit(0);
        });
    }

    private HBox createHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(12, 15, 12, 15));
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);

        // Title
        Label title = new Label("Tunneler");
        title.getStyleClass().add("header-title");

        // Domain input section
        HBox domainBox = new HBox(6);
        domainBox.setAlignment(Pos.CENTER_LEFT);

        Label domainLabel = new Label("Domain:");
        domainLabel.getStyleClass().add("domain-label");

        TextField domainInput = new TextField();
        domainInput.textProperty().bindBidirectional(config.domainProperty());
        domainInput.setPromptText("yourdomain");
        domainInput.getStyleClass().add("domain-input");
        domainInput.setPrefWidth(140);

        // Validate domain input
        domainInput.textProperty().addListener((obs, old, newVal) -> {
            String sanitized = newVal.replaceAll("[^a-zA-Z0-9-]", "");
            if (!sanitized.equals(newVal)) {
                domainInput.setText(sanitized);
            }
        });

        Label domainSuffix = new Label(".inthespace.online");
        domainSuffix.getStyleClass().add("domain-suffix");

        domainBox.getChildren().addAll(domainLabel, domainInput, domainSuffix);

        // Spacer
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        // Connect/Disconnect button
        Button connectButton = new Button("Connect");
        connectButton.getStyleClass().addAll("btn", "btn-success");
        connectButton.setMinWidth(100);
        connectButton.setOnAction(e -> {
            if (isConnected) {
                disconnectTunnel();
                connectButton.setText("Connect");
                connectButton.getStyleClass().remove("btn-danger");
                connectButton.getStyleClass().add("btn-success");
            } else {
                startTunnelClient();
                connectButton.setText("Disconnect");
                connectButton.getStyleClass().remove("btn-success");
                connectButton.getStyleClass().add("btn-danger");
            }
        });

        // Status indicator
        HBox statusBox = new HBox(6);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.getStyleClass().add("status-indicator");

        Label statusDot = new Label("●");
        statusDot.getStyleClass().add("status-dot");

        Label statusText = new Label(config.getSignalHost());
        statusText.getStyleClass().add("status-text");

        statusBox.getChildren().addAll(statusDot, statusText);

        // Save button
        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("btn", "btn-primary");
        saveButton.setMinWidth(80);
        saveButton.setOnAction(e -> {
            ConfigManager.saveConfig();
            showAlert("Success", "Configuration saved!");
        });

        header.getChildren().addAll(title, domainBox, spacer1, connectButton, statusBox, saveButton);
        return header;
    }

    private TabPane createTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: Routing Rules
        Tab routingTab = new Tab("Routing Rules");
        routingTab.setContent(new RoutingRulesPanel(config));

        // Tab 2: Connection Monitor
        Tab monitorTab = new Tab("Connection Monitor");
        monitorTab.setContent(new ConnectionMonitorPanel());

        // Tab 3: Raw Mode
        Tab rawModeTab = new Tab("Raw Mode");
        rawModeTab.setContent(new RawModePanel(config));

        // Tab 4: Settings
        Tab settingsTab = new Tab("Settings");
        settingsTab.setContent(new SettingsPanel(config));

        tabPane.getTabs().addAll(routingTab, monitorTab, rawModeTab, settingsTab);
        return tabPane;
    }

    private void startTunnelClient() {
        if (isConnected)
            return;

        tunnelClient = new TunnelClient();
        tunnelClient.connect(); // Starts in background thread
        isConnected = true;
    }

    private void disconnectTunnel() {
        if (!isConnected)
            return;

        if (tunnelClient != null) {
            tunnelClient.disconnect();
        }
        isConnected = false;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        // Load configuration before starting UI
        ConfigManager.loadConfig();
        launch(args);
    }
}
