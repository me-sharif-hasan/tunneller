package com.tunneler.ui;

import com.tunneler.router.RouterConfig;
import com.tunneler.router.RoutingRule;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Dialog for adding/editing routing rules with autocomplete and smart defaults
 */
public class RouteDialog extends Dialog<RoutingRule> {

    private TextField pathField;
    private ComboBox<String> hostCombo;
    private ComboBox<Integer> portCombo;
    private TextField descField;
    private CheckBox stripCheckbox;

    public RouteDialog(RouterConfig config, RoutingRule existingRule) {
        setTitle(existingRule == null ? "Add Route" : "Edit Route");
        setHeaderText(existingRule == null ? "Add a new routing rule" : "Edit routing rule");

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Path pattern
        Label pathLabel = new Label("Path Pattern:");
        pathField = new TextField();
        pathField.setPromptText("/api/user/*");
        if (existingRule != null) {
            pathField.setText(existingRule.getPathPattern());
        }

        // Target host with autocomplete
        Label hostLabel = new Label("Target Host:");
        hostCombo = new ComboBox<>();
        hostCombo.setEditable(true);
        hostCombo.getItems().addAll(config.getTargetHostHistory());
        hostCombo.setPromptText("localhost or 192.168.1.10");
        if (existingRule != null) {
            hostCombo.setValue(existingRule.getTargetHost());
        }

        // Target port with autocomplete
        Label portLabel = new Label("Target Port:");
        portCombo = new ComboBox<>();
        portCombo.setEditable(true);
        portCombo.getItems().addAll(config.getTargetPortHistory());
        portCombo.getItems().addAll(8080, 3000, 80, 443, 5000);
        portCombo.setPromptText("80 (auto for http), 443 (auto for https)");
        if (existingRule != null) {
            portCombo.setValue(existingRule.getTargetPort());
        }

        // Strip Prefix checkbox
        Label stripLabel = new Label("Strip Prefix:");
        stripCheckbox = new CheckBox("Remove matched path pattern before forwarding");
        if (existingRule != null) {
            stripCheckbox.setSelected(existingRule.isStripPrefix());
        }

        Label stripHintLabel = new Label("Example: /api/users/* with input /api/users/123 → forwards /users/123");
        stripHintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");
        stripHintLabel.setWrapText(true);

        VBox stripBox = new VBox(5);
        stripBox.getChildren().addAll(stripCheckbox, stripHintLabel);

        // Description
        Label descLabel = new Label("Description:");
        descField = new TextField();
        descField.setPromptText("Team Member 1 - Service");
        if (existingRule != null) {
            descField.setText(existingRule.getDescription());
        }

        grid.add(pathLabel, 0, 0);
        grid.add(pathField, 1, 0);
        grid.add(hostLabel, 0, 1);
        grid.add(hostCombo, 1, 1);
        grid.add(portLabel, 0, 2);
        grid.add(portCombo, 1, 2);
        grid.add(stripLabel, 0, 3);
        grid.add(stripBox, 1, 3);
        grid.add(descLabel, 0, 4);
        grid.add(descField, 1, 4);

        getDialogPane().setContent(grid);

        // Buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Convert result
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    String path = pathField.getText().trim();
                    String host = hostCombo.getValue();

                    // Handle port - ComboBox can return either String or Integer
                    Integer port = null;
                    Object portValue = portCombo.getValue();
                    if (portValue instanceof Integer) {
                        port = (Integer) portValue;
                    } else if (portValue instanceof String) {
                        String portStr = (String) portValue;
                        if (!portStr.isEmpty()) {
                            port = Integer.parseInt(portStr);
                        }
                    }

                    // Smart port defaults based on host (http→80, https→443)
                    if (port == null && host != null && !host.isEmpty()) {
                        if (host.toLowerCase().startsWith("https://") || host.contains(":443")) {
                            port = 443;
                        } else if (host.toLowerCase().startsWith("http://") || !host.contains("://")) {
                            port = 80;
                        }
                    }

                    // Clean up host (remove http:// or https:// prefix)
                    if (host != null) {
                        host = host.replaceFirst("^https?://", "");
                    }

                    String desc = descField.getText().trim();
                    boolean stripPrefix = stripCheckbox.isSelected();

                    if (path.isEmpty() || host == null || host.isEmpty()) {
                        showAlert("Validation Error", "Path and host are required.");
                        return null;
                    }

                    if (port == null) {
                        showAlert("Validation Error",
                                "Port is required.\n\nTip: Defaults to 80 for http://, 443 for https://");
                        return null;
                    }

                    if (port < 1 || port > 65535) {
                        showAlert("Validation Error", "Port must be between 1 and 65535.");
                        return null;
                    }

                    return new RoutingRule(path, host, port, desc, stripPrefix);
                } catch (NumberFormatException e) {
                    showAlert("Error", "Port must be a valid number.");
                    return null;
                } catch (Exception e) {
                    showAlert("Error", "Invalid input: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
