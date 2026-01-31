package com.tunneller.ui;

import com.tunneller.monitor.ConnectionStats;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Connection Monitor panel with real-time graphs
 * Updates in background thread to avoid blocking network performance
 */
public class ConnectionMonitorPanel extends VBox {

    private LineChart<Number, Number> chart;
    private Map<String, XYChart.Series<Number, Number>> seriesMap = new HashMap<>();
    private long startTime = System.currentTimeMillis();
    private int dataPointCounter = 0;

    public ConnectionMonitorPanel() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label title = new Label("Real-Time Connection Monitor");
        title.getStyleClass().add("section-title");

        // Create chart
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time (seconds)");
        xAxis.setAutoRanging(true);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Requests per Second");
        yAxis.setAutoRanging(true);

        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Connections per Route");
        chart.setCreateSymbols(false);
        chart.setAnimated(false); // Disable animation for better performance
        chart.setPrefHeight(500);

        getChildren().addAll(title, chart);

        // Start background update thread (virtual thread for lightweight)
        Thread.startVirtualThread(this::updateLoop);
    }

    /**
     * Background update loop - runs in virtual thread
     * Does NOT block network performance
     */
    private void updateLoop() {
        while (true) {
            try {
                // Update every 1 second
                Thread.sleep(1000);

                // Get stats from connection tracker
                Map<String, ConnectionStats.RouteStats> stats = ConnectionStats.getInstance().getRouteStats();

                // Update UI on JavaFX thread
                Platform.runLater(() -> updateChart(stats));

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void updateChart(Map<String, ConnectionStats.RouteStats> stats) {
        double currentTime = (System.currentTimeMillis() - startTime) / 1000.0;

        for (Map.Entry<String, ConnectionStats.RouteStats> entry : stats.entrySet()) {
            String routePattern = entry.getKey();
            ConnectionStats.RouteStats routeStats = entry.getValue();

            // Get or create series for this route
            XYChart.Series<Number, Number> series = seriesMap.computeIfAbsent(routePattern, pattern -> {
                XYChart.Series<Number, Number> newSeries = new XYChart.Series<>();
                newSeries.setName(pattern);
                chart.getData().add(newSeries);
                return newSeries;
            });

            // Add data point (requests per minute / 60 = requests per second)
            double requestsPerSecond = routeStats.getRequestsPerMinute() / 60.0;
            series.getData().add(new XYChart.Data<>(currentTime, requestsPerSecond));

            // Keep only last 60 data points (1 minute of history)
            if (series.getData().size() > 60) {
                series.getData().remove(0);
            }
        }

        dataPointCounter++;
    }
}
