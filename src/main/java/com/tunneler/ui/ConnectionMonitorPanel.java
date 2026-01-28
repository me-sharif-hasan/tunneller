package com.tunneler.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Connection Monitor panel (Tab 2)
 * Shows real-time active connections
 */
public class ConnectionMonitorPanel extends VBox {

    public ConnectionMonitorPanel() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label title = new Label("Active Connections");
        title.getStyleClass().add("section-title");

        Label emptyState = new Label("No active connections");
        emptyState.getStyleClass().add("empty-state");

        Label infoLabel = new Label("📊 Real-time connection monitoring will appear here when requests are routed.");
        infoLabel.getStyleClass().add("info-label");
        infoLabel.setWrapText(true);

        getChildren().addAll(title, emptyState, infoLabel);

        // TODO: Implement real-time connection monitoring
        // This would require tracking active connections in TunnelClient
        // and using JavaFX Properties/ObservableList to update UI
    }
}
