package com.tunneller.ui;

import com.tunneller.router.RouterConfig;
import com.tunneller.router.RoutingRule;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

/**
 * Routing Rules panel (Tab 1)
 */
public class RoutingRulesPanel extends VBox {

    private RouterConfig config;
    private TableView<RoutingRule> table;

    public RoutingRulesPanel(RouterConfig config) {
        this.config = config;
        setPadding(new Insets(20));
        setSpacing(10);

        // Toolbar
        HBox toolbar = createToolbar();
        getChildren().add(toolbar);

        // Table (96% width, centered)
        table = createTable();
        table.setMaxWidth(Double.MAX_VALUE * 0.96); // 96% of available width

        HBox tableContainer = new HBox(table);
        tableContainer.setAlignment(javafx.geometry.Pos.CENTER);
        HBox.setHgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        getChildren().add(tableContainer);

        // Info box
        Label infoLabel = new Label("Tip: Click Edit or Delete buttons in the Actions column for each route.");
        infoLabel.getStyleClass().add("info-label");
        infoLabel.setWrapText(true);
        getChildren().add(infoLabel);
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        Button addButton = new Button("Add Route");
        addButton.getStyleClass().addAll("btn", "btn-primary");
        addButton.setOnAction(e -> addRoute());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button importButton = new Button("Import");
        importButton.getStyleClass().add("btn");

        Button exportButton = new Button("Export");
        exportButton.getStyleClass().add("btn");

        toolbar.getChildren().addAll(addButton, spacer, importButton, exportButton);
        return toolbar;
    }

    private TableView<RoutingRule> createTable() {
        TableView<RoutingRule> table = new TableView<>();
        table.setItems(config.getRoutingRules());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<RoutingRule, String> pathCol = new TableColumn<>("Path Pattern");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("pathPattern"));
        pathCol.setMinWidth(150);

        TableColumn<RoutingRule, String> hostCol = new TableColumn<>("Target Host");
        hostCol.setCellValueFactory(new PropertyValueFactory<>("targetHost"));
        hostCol.setMinWidth(120);

        TableColumn<RoutingRule, Integer> portCol = new TableColumn<>("Port");
        portCol.setCellValueFactory(new PropertyValueFactory<>("targetPort"));
        portCol.setMinWidth(60);
        portCol.setMaxWidth(80);

        TableColumn<RoutingRule, Integer> priorityCol = new TableColumn<>("Priority");
        priorityCol.setCellValueFactory(new PropertyValueFactory<>("priority"));
        priorityCol.setMinWidth(60);
        priorityCol.setMaxWidth(80);

        TableColumn<RoutingRule, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setMinWidth(150);

        // Sort table by priority (ascending - low numbers first)
        table.setItems(config.getRoutingRules().sorted((r1, r2) -> {
            int priorityCompare = Integer.compare(r1.getPriority(), r2.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // If same priority, sort by path pattern for consistency
            return r1.getPathPattern().compareTo(r2.getPathPattern());
        }));

        // Actions column with Edit and Delete buttons (fixed width, full button text)
        TableColumn<RoutingRule, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setMinWidth(170);
        actionsCol.setPrefWidth(170);
        actionsCol.setResizable(false);
        actionsCol.setCellFactory(param -> new TableCell<>() {
            private final HBox buttonBox = new HBox(8);
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");

            {
                editButton.getStyleClass().add("btn");
                editButton.setMinWidth(70);
                editButton.setPrefWidth(70);

                deleteButton.getStyleClass().add("btn");
                deleteButton.setMinWidth(70);
                deleteButton.setPrefWidth(70);

                editButton.setOnAction(event -> {
                    RoutingRule rule = getTableView().getItems().get(getIndex());
                    editRouteInline(rule);
                });

                deleteButton.setOnAction(event -> {
                    RoutingRule rule = getTableView().getItems().get(getIndex());
                    deleteRouteInline(rule);
                });

                buttonBox.getChildren().addAll(editButton, deleteButton);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(buttonBox);
                }
            }
        });

        table.getColumns().addAll(pathCol, hostCol, portCol, priorityCol, descCol, actionsCol);
        return table;
    }

    private void editRouteInline(RoutingRule rule) {
        RouteDialog dialog = new RouteDialog(config, rule);
        dialog.showAndWait().ifPresent(updatedRule -> {
            config.removeRoutingRule(rule);
            config.addRoutingRule(updatedRule);
        });
    }

    private void deleteRouteInline(RoutingRule rule) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete routing rule?");
        confirm.setContentText("Are you sure you want to delete: " + rule.getPathPattern());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                config.removeRoutingRule(rule);
            }
        });
    }

    private void addRoute() {
        RouteDialog dialog = new RouteDialog(config, null);
        dialog.showAndWait().ifPresent(rule -> {
            config.addRoutingRule(rule);
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
