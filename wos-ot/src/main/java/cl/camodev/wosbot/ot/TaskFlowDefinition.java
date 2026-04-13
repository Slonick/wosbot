package cl.camodev.wosbot.ot;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete user-built automation task definition.
 * Contains an ordered list of {@link TaskFlowNode} steps that will
 * be executed sequentially when the task runs.
 *
 * <p>This object is designed to be serialized/deserialized as JSON
 * for save/load/share functionality.</p>
 */
public class TaskFlowDefinition {

    private String name;
    private String description;
    private String startLocation; // HOME, WORLD, ANY
    private List<TaskFlowNode> nodes;
    private long createdAt;
    private long updatedAt;

    public TaskFlowDefinition() {
        this.nodes = new ArrayList<>();
        this.startLocation = "ANY";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public TaskFlowDefinition(String name) {
        this();
        this.name = name;
    }

    // ========== Node Management ==========

    /**
     * Adds a node to the end of the flow and assigns it a unique ID based on the max existing ID.
     */
    public TaskFlowNode addNode(TaskFlowNode node) {
        int maxId = 0;
        for (TaskFlowNode n : nodes) {
            if (n.getId() > maxId) maxId = n.getId();
        }
        node.setId(maxId + 1);
        nodes.add(node);
        this.updatedAt = System.currentTimeMillis();
        return node;
    }

    /**
     * Removes a node by its index in the list. Does NOT reassign IDs to preserve existing graph references.
     */
    public void removeNode(int index) {
        if (index >= 0 && index < nodes.size()) {
            nodes.remove(index);
            this.updatedAt = System.currentTimeMillis();
        }
    }

    /**
     * Returns the next node ID that would be assigned.
     */
    public int getNextNodeId() {
        return nodes.size() + 1;
    }

    // ========== Getters & Setters ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(String startLocation) {
        this.startLocation = startLocation;
    }

    public List<TaskFlowNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<TaskFlowNode> nodes) {
        this.nodes = nodes;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return String.format("TaskFlow[%s] (%d nodes)", name, nodes.size());
    }
}
