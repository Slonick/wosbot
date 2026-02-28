package cl.camodev.wosbot.common.view;

import cl.camodev.wosbot.ot.DTOPriorityItem;
import cl.camodev.utiles.StyleConstants;
import javafx.animation.AnimationTimer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic custom control to manage priority lists.
 * Can be used for purchase priorities, tasks, events, etc.
 * Features:
 * - Enable/Disable items
 * - Sort by priority (1 = highest priority)
 * - Move items up/down with buttons
 * - Drag and drop items to reorder
 * - Auto-scroll when dragging near edges
 */
public class PriorityListView extends HBox {

    private static final DataFormat PRIORITY_DATA_FORMAT = new DataFormat("application/x-java-priority-item");
    private static final double SCROLL_ZONE_HEIGHT = 80.0; // Pixels from edge to trigger scroll (increased for smoother gradient)
    private static final double MAX_SCROLL_SPEED = 0.015; // Maximum scroll speed at the edge (reduced from 0.05)
    private static final double MIN_SCROLL_SPEED = 0.002; // Minimum scroll speed at the zone boundary (reduced from 0.005)

    private final ListView<DTOPriorityItem> listView;
    private final ObservableList<DTOPriorityItem> items;
    private Runnable onChangeCallback;
    private AnimationTimer autoScrollTimer;
    private double scrollDirection = 0; // -1 = up, 1 = down, 0 = no scroll
    private double scrollSpeed = 0; // Current scroll speed (proportional to distance from edge)

    public PriorityListView() {
        this.items = FXCollections.observableArrayList();
        this.listView = new ListView<>(items);

        setupListView();
        setupLayout();
        applyDarkStyles();
        setupAutoScroll();
    }

    private void setupListView() {
        listView.setCellFactory(lv -> new PriorityItemCell());
        listView.setPrefHeight(200);
        listView.setFixedCellSize(32); // Smaller cell height
    }

    private void setupLayout() {
        HBox.setHgrow(listView, Priority.ALWAYS);

        // Control buttons in a vertical box on the right
        VBox controls = new VBox(5);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(0, 0, 0, 5));

        Button moveUpBtn = new Button("↑");
        Button moveDownBtn = new Button("↓");

        // Make buttons smaller and square
        moveUpBtn.setPrefSize(35, 35);
        moveDownBtn.setPrefSize(35, 35);
        moveUpBtn.setMinSize(35, 35);
        moveDownBtn.setMinSize(35, 35);

        moveUpBtn.setOnAction(e -> moveSelectedItem(-1));
        moveDownBtn.setOnAction(e -> moveSelectedItem(1));

        controls.getChildren().addAll(moveUpBtn, moveDownBtn);

        this.getChildren().addAll(listView, controls);
    }

    /**
     * Apply dark theme styles to the list view
     */
    private void applyDarkStyles() {
        // Apply CSS class instead of inline styles
        listView.getStyleClass().add(StyleConstants.PRIORITY_LIST_VIEW);
    }

    /**
     * Sets the items in the list
     */
    public void setItems(List<DTOPriorityItem> priorities) {
        items.clear();
        if (priorities != null && !priorities.isEmpty()) {
            items.addAll(priorities);
            sortByPriority();
        }
    }

    /**
     * Gets the current items
     */
    public List<DTOPriorityItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Moves an item up or down
     */
    private void moveSelectedItem(int direction) {
        int selectedIndex = listView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        int newIndex = selectedIndex + direction;
        if (newIndex < 0 || newIndex >= items.size()) {
            return;
        }

        DTOPriorityItem item = items.get(selectedIndex);
        items.remove(selectedIndex);
        items.add(newIndex, item);

        updatePriorities();
        listView.getSelectionModel().select(newIndex);

        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    /**
     * Updates priorities based on list order
     */
    private void updatePriorities() {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPriority(i + 1);
        }
    }

    /**
     * Sorts items by priority
     */
    private void sortByPriority() {
        FXCollections.sort(items, Comparator.comparingInt(DTOPriorityItem::getPriority));
    }

    /**
     * Sets a callback that will be executed when there are changes
     */
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    /**
     * Converts the list to String to store in configuration
     * Format: "item1:priority1:enabled1|item2:priority2:enabled2|..."
     */
    public String toConfigString() {
        return items.stream()
                .map(DTOPriorityItem::toConfigString)
                .collect(Collectors.joining("|"));
    }

    /**
     * Loads the list from a configuration String
     */
    public void fromConfigString(String configString) {
        items.clear();

        if (configString == null || configString.trim().isEmpty()) {
            return;
        }

        String[] itemStrings = configString.split("\\|");
        for (String itemString : itemStrings) {
            DTOPriorityItem priority = DTOPriorityItem.fromConfigString(itemString);
            if (priority != null) {
                items.add(priority);
            }
        }

        sortByPriority();
    }

    /**
     * Sets up the auto-scroll animation timer
     */
    private void setupAutoScroll() {
        autoScrollTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (scrollDirection != 0 && scrollSpeed > 0) {
                    ScrollBar scrollBar = getVerticalScrollBar();
                    if (scrollBar != null && scrollBar.isVisible()) {
                        double currentValue = scrollBar.getValue();
                        double newValue = currentValue + (scrollDirection * scrollSpeed);
                        newValue = Math.max(scrollBar.getMin(), Math.min(scrollBar.getMax(), newValue));
                        scrollBar.setValue(newValue);
                    }
                }
            }
        };
    }

    /**
     * Gets the vertical scroll bar from the ListView
     */
    private ScrollBar getVerticalScrollBar() {
        for (var node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar scrollBar) {
                if (scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                    return scrollBar;
                }
            }
        }
        return null;
    }

    /**
     * Custom cell to display each item with drag and drop support
     */
    private class PriorityItemCell extends ListCell<DTOPriorityItem> {

        private final HBox content;
        private final CheckBox enabledCheckBox;
        private final Label priorityLabel;
        private final Label nameLabel;
        private final Label dragIndicator;

        public PriorityItemCell() {
            content = new HBox(8);
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPadding(new Insets(3, 8, 3, 8));

            // Visual drag indicator
            dragIndicator = new Label("☰");
            dragIndicator.setMinWidth(18);

            enabledCheckBox = new CheckBox();
            priorityLabel = new Label();
            priorityLabel.setMinWidth(25);

            nameLabel = new Label();
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            content.getChildren().addAll(dragIndicator, enabledCheckBox, priorityLabel, nameLabel);

            // Apply CSS class to the cell
            getStyleClass().add(StyleConstants.PRIORITY_LIST_CELL);

            setupDragAndDrop();
        }

        private void setupDragAndDrop() {
            // Setup drag start
            setOnDragDetected(event -> {
                if (getItem() == null) {
                    return;
                }

                Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.put(PRIORITY_DATA_FORMAT, getIndex());
                dragboard.setContent(content);

                // Start auto-scroll timer
                autoScrollTimer.start();

                event.consume();
            });

            // Update style based on cursor position and handle auto-scroll
            setOnDragOver(event -> {
                if (event.getGestureSource() != this &&
                        event.getDragboard().hasContent(PRIORITY_DATA_FORMAT)) {
                    event.acceptTransferModes(TransferMode.MOVE);

                    // Calculate mouse position relative to ListView
                    double mouseYInListView = event.getSceneY() - listView.localToScene(0, 0).getY();
                    double listViewHeight = listView.getHeight();

                    // Determine scroll direction and speed based on mouse position
                    if (mouseYInListView < SCROLL_ZONE_HEIGHT) {
                        // Near top edge - scroll up
                        scrollDirection = -1;
                        // Calculate proportional speed: closer to edge = faster
                        // Distance from top edge (0 = at edge, SCROLL_ZONE_HEIGHT = at boundary)
                        // Normalize to 0-1 range (1 = at edge, 0 = at boundary)
                        double proximity = 1.0 - (mouseYInListView / SCROLL_ZONE_HEIGHT);
                        // Calculate speed: interpolate between MIN and MAX based on proximity
                        scrollSpeed = MIN_SCROLL_SPEED + (proximity * (MAX_SCROLL_SPEED - MIN_SCROLL_SPEED));
                    } else if (mouseYInListView > listViewHeight - SCROLL_ZONE_HEIGHT) {
                        // Near bottom edge - scroll down
                        scrollDirection = 1;
                        // Calculate proportional speed: closer to edge = faster
                        // Distance from bottom edge (0 = at edge, SCROLL_ZONE_HEIGHT = at boundary)
                        double distanceFromEdge = listViewHeight - mouseYInListView;
                        // Normalize to 0-1 range (1 = at edge, 0 = at boundary)
                        double proximity = 1.0 - (distanceFromEdge / SCROLL_ZONE_HEIGHT);
                        // Calculate speed: interpolate between MIN and MAX based on proximity
                        scrollSpeed = MIN_SCROLL_SPEED + (proximity * (MAX_SCROLL_SPEED - MIN_SCROLL_SPEED));
                    } else {
                        // In the middle - no scroll
                        scrollDirection = 0;
                        scrollSpeed = 0;
                    }

                    // Determine if cursor is in upper or lower half of this cell
                    double mouseY = event.getY();
                    double cellHeight = getHeight();

                    // Remove previous drag classes
                    getStyleClass().removeAll(
                            StyleConstants.PRIORITY_LIST_CELL_DRAG_OVER_TOP,
                            StyleConstants.PRIORITY_LIST_CELL_DRAG_OVER_BOTTOM
                    );

                    if (mouseY < cellHeight / 2) {
                        // Upper half - show border on top
                        getStyleClass().add(StyleConstants.PRIORITY_LIST_CELL_DRAG_OVER_TOP);
                    } else {
                        // Lower half - show border on bottom
                        getStyleClass().add(StyleConstants.PRIORITY_LIST_CELL_DRAG_OVER_BOTTOM);
                    }
                }
                event.consume();
            });

            // Restore style when exiting cell
            setOnDragExited(event -> {
                getStyleClass().removeAll(
                        StyleConstants.PRIORITY_LIST_CELL_DRAG_OVER_TOP,
                        StyleConstants.PRIORITY_LIST_CELL_DRAG_OVER_BOTTOM
                );
                event.consume();
            });

            // Setup drop event
            setOnDragDropped(event -> {
                if (getItem() == null) {
                    return;
                }

                Dragboard db = event.getDragboard();
                boolean success = false;

                if (db.hasContent(PRIORITY_DATA_FORMAT)) {
                    int draggedIdx = (Integer) db.getContent(PRIORITY_DATA_FORMAT);
                    int thisIdx = getIndex();

                    // Determine if cursor is in upper or lower half
                    double mouseY = event.getY();
                    double cellHeight = getHeight();
                    boolean dropAbove = mouseY < cellHeight / 2;

                    DTOPriorityItem draggedItem = items.get(draggedIdx);
                    items.remove(draggedIdx);

                    // Calculate target index
                    int targetIdx = thisIdx;

                    if (draggedIdx < thisIdx) {
                        // Item dragged from top to bottom
                        if (dropAbove) {
                            targetIdx = thisIdx - 1;
                        }
                    } else {
                        // Item dragged from bottom to top
                        if (!dropAbove) {
                            targetIdx = thisIdx + 1;
                        }
                    }

                    // Ensure index is within valid range
                    targetIdx = Math.max(0, Math.min(targetIdx, items.size()));

                    items.add(targetIdx, draggedItem);
                    updatePriorities();

                    listView.getSelectionModel().select(targetIdx);

                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }

                    success = true;
                }

                // Stop auto-scroll
                scrollDirection = 0;
                scrollSpeed = 0;
                autoScrollTimer.stop();

                event.setDropCompleted(success);
                event.consume();
            });

            // Finalize drag
            setOnDragDone(event -> {
                getStyleClass().removeAll(
                        StyleConstants.PRIORITY_LIST_CELL_DRAG_OVER_TOP,
                        StyleConstants.PRIORITY_LIST_CELL_DRAG_OVER_BOTTOM
                );

                // Stop auto-scroll
                scrollDirection = 0;
                scrollSpeed = 0;
                autoScrollTimer.stop();

                event.consume();
            });
        }

        @Override
        protected void updateItem(DTOPriorityItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                // Keep base CSS class
                getStyleClass().setAll(StyleConstants.PRIORITY_LIST_CELL);
            } else {
                enabledCheckBox.setSelected(item.isEnabled());

                // Calculate visible priority (only count enabled items)
                int visiblePriority = calculateVisiblePriority(item);

                // Show priority number only if enabled
                if (item.isEnabled()) {
                    priorityLabel.setText(visiblePriority + ".");
                } else {
                    priorityLabel.setText("--");
                }

                nameLabel.setText(item.getName());

                // Clear previous listeners to avoid duplicates
                enabledCheckBox.setOnAction(null);

                // Listener for checkbox changes - this updates the visual state
                enabledCheckBox.setOnAction(e -> {
                    item.setEnabled(enabledCheckBox.isSelected());
                    // Update visual state immediately
                    updateVisualState(item.isEnabled());
                    // Refresh all cells to update numbering
                    listView.refresh();
                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }
                });

                // Update visual state based on enabled status
                updateVisualState(item.isEnabled());

                setGraphic(content);
            }
        }

        /**
         * Calculates the visible priority number for an item
         * Only counts enabled items that appear before this item
         */
        private int calculateVisiblePriority(DTOPriorityItem currentItem) {
            int visiblePriority = 0;
            for (DTOPriorityItem item : items) {
                if (item.isEnabled()) {
                    visiblePriority++;
                    if (item == currentItem) {
                        break;
                    }
                }
            }
            return visiblePriority;
        }

        /**
         * Updates the visual state of all labels based on enabled status
         * Uses CSS classes for persistent styling
         */
        private void updateVisualState(boolean enabled) {
            // Update drag indicator style classes
            dragIndicator.getStyleClass().removeAll(
                    StyleConstants.PRIORITY_DRAG_INDICATOR,
                    StyleConstants.PRIORITY_DRAG_INDICATOR_DISABLED
            );
            dragIndicator.getStyleClass().add(
                    enabled ? StyleConstants.PRIORITY_DRAG_INDICATOR : StyleConstants.PRIORITY_DRAG_INDICATOR_DISABLED
            );

            // Update priority label style classes
            priorityLabel.getStyleClass().removeAll(
                    StyleConstants.PRIORITY_LABEL,
                    StyleConstants.PRIORITY_LABEL_DISABLED
            );
            priorityLabel.getStyleClass().add(
                    enabled ? StyleConstants.PRIORITY_LABEL : StyleConstants.PRIORITY_LABEL_DISABLED
            );

            // Update name label style classes
            nameLabel.getStyleClass().removeAll(
                    StyleConstants.PRIORITY_NAME_LABEL,
                    StyleConstants.PRIORITY_NAME_LABEL_DISABLED
            );
            nameLabel.getStyleClass().add(
                    enabled ? StyleConstants.PRIORITY_NAME_LABEL : StyleConstants.PRIORITY_NAME_LABEL_DISABLED
            );
        }
    }
}
