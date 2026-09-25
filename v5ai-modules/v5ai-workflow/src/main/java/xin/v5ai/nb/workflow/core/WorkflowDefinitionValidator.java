package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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
            if (node.type() == null) throw new IllegalArgumentException("node type is required: " + node.id());
            if (byId.putIfAbsent(node.id(), node) != null) throw new IllegalArgumentException("duplicate node id: " + node.id());
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
        Map<String, List<WorkflowEdge>> outgoing = new HashMap<>();
        Set<String> edgeIds = new HashSet<>();
        for (WorkflowEdge edge : definition.edges() == null ? java.util.List.<WorkflowEdge>of() : definition.edges()) {
            if (edge.id() == null || edge.id().isBlank() || !edgeIds.add(edge.id())) {
                throw new IllegalArgumentException("edge id is required and must be unique");
            }
            if (!byId.containsKey(edge.source())) {
                throw new IllegalArgumentException("edge references unknown source node: " + edge.source());
            }
            if (!byId.containsKey(edge.target())) {
                throw new IllegalArgumentException("edge references unknown target node: " + edge.target());
            }
            adjacency.computeIfAbsent(edge.source(), k -> new HashSet<>()).add(edge.target());
            outgoing.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
        }
        for (WorkflowNode node : definition.nodes()) {
            List<WorkflowEdge> edges = outgoing.getOrDefault(node.id(), List.of());
            if (edges.isEmpty() && node.type() != WorkflowNodeType.END) {
                throw new IllegalArgumentException("only END nodes may have no outgoing edge: " + node.id());
            }
            if (node.type() == WorkflowNodeType.END && !edges.isEmpty()) throw new IllegalArgumentException("END node cannot have outgoing edges: " + node.id());
            if (node.type() == WorkflowNodeType.CONDITION) {
                Set<String> handles = new HashSet<>();
                edges.forEach(edge -> handles.add(edge.sourceHandle()));
                if (edges.size() != 2 || !handles.equals(Set.of("true", "false"))) {
                    throw new IllegalArgumentException("CONDITION node must have exactly true and false outgoing edges: " + node.id());
                }
            } else if (node.type() != WorkflowNodeType.END && edges.size() > 1) {
                throw new IllegalArgumentException("node type does not support multiple outgoing edges: " + node.id());
            }
        }
        if (hasCycle(adjacency, byId.keySet())) {
            throw new IllegalArgumentException("workflow must not contain cycles");
        }
        String startId = definition.nodes().stream().filter(n -> n.type() == WorkflowNodeType.START).findFirst().orElseThrow().id();
        Set<String> reachable = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (reachable.add(current)) queue.addAll(adjacency.getOrDefault(current, Set.of()));
        }
        if (reachable.size() != byId.size()) throw new IllegalArgumentException("workflow contains nodes unreachable from START");
    }

    public static WorkflowValidationResult validateDetailed(WorkflowDefinition definition) {
        try {
            validate(definition);
            return new WorkflowValidationResult(true, List.of());
        } catch (IllegalArgumentException exception) {
            return new WorkflowValidationResult(false, List.of(new WorkflowDiagnostic(
                    "ERROR", "WORKFLOW_DEFINITION_INVALID", null, null, exception.getMessage())));
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
