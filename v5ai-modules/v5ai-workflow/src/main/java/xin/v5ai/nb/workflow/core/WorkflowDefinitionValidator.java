package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * DAG 结构校验：至少一个节点、恰好一个 START、至少一个 END、边指向存在节点、无环。
 */
public final class WorkflowDefinitionValidator {
    private WorkflowDefinitionValidator() {
    }

    public static void validate(WorkflowDefinition definition) {
        if (definition == null || definition.nodes() == null || definition.nodes().isEmpty()) {
            throw new IllegalArgumentException("workflow must contain at least one node");
        }
        Map<String, WorkflowNode> byId = new HashMap<>();
        for (WorkflowNode node : definition.nodes()) {
            if (node.id() == null || node.id().isBlank()) {
                throw new IllegalArgumentException("node id is required");
            }
            byId.put(node.id(), node);
        }
        long startCount = definition.nodes().stream().filter(n -> n.type() == WorkflowNodeType.START).count();
        if (startCount != 1) {
            throw new IllegalArgumentException("workflow must contain exactly one START node");
        }
        boolean hasEnd = definition.nodes().stream().anyMatch(n -> n.type() == WorkflowNodeType.END);
        if (!hasEnd) {
            throw new IllegalArgumentException("workflow must contain at least one END node");
        }

        Map<String, Set<String>> adjacency = new HashMap<>();
        for (WorkflowEdge edge : definition.edges() == null ? java.util.List.<WorkflowEdge>of() : definition.edges()) {
            if (!byId.containsKey(edge.source())) {
                throw new IllegalArgumentException("edge references unknown source node: " + edge.source());
            }
            if (!byId.containsKey(edge.target())) {
                throw new IllegalArgumentException("edge references unknown target node: " + edge.target());
            }
            adjacency.computeIfAbsent(edge.source(), k -> new HashSet<>()).add(edge.target());
        }
        if (hasCycle(adjacency, byId.keySet())) {
            throw new IllegalArgumentException("workflow must not contain cycles");
        }
    }

    private static boolean hasCycle(Map<String, Set<String>> adjacency, Set<String> nodes) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : nodes) {
            if (dfs(node, adjacency, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfs(String node, Map<String, Set<String>> adjacency,
                               Set<String> visiting, Set<String> visited) {
        if (visiting.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visiting.add(node);
        for (String next : adjacency.getOrDefault(node, Set.of())) {
            if (dfs(next, adjacency, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }
}
