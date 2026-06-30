package ru.wisla.fm.rules.api;



import org.springframework.stereotype.Component;

import ru.wisla.fm.common.api.ErrorResponse;

import ru.wisla.fm.processing.canvas.CanvasEdgeView;

import ru.wisla.fm.processing.canvas.CanvasNodeView;



import java.util.ArrayDeque;

import java.util.HashMap;

import java.util.HashSet;

import java.util.List;

import java.util.Map;

import java.util.Queue;

import java.util.Set;



@Component

public class RuleCanvasValidator {



    private static final Set<String> ACTION_TYPES =

            Set.of("dedup", "threshold", "correlation", "notify", "push");



    public void validate(RuleCanvasDto canvas) {

        if (canvas == null) {

            return;

        }

        List<Map<String, Object>> rawNodes = canvas.nodes() != null ? canvas.nodes() : List.of();

        List<Map<String, Object>> rawEdges = canvas.edges() != null ? canvas.edges() : List.of();

        if (rawNodes.isEmpty()) {

            return;

        }



        Map<String, CanvasNodeView> nodesById = new HashMap<>();

        for (Map<String, Object> raw : rawNodes) {

            CanvasNodeView node = CanvasNodeView.fromMap(raw);

            if (node.id() != null && !node.id().isBlank()) {

                nodesById.put(node.id(), node);

            }

        }



        String triggerId = nodesById.values().stream()

                .filter(CanvasNodeView::isTriggerStream)

                .map(CanvasNodeView::id)

                .findFirst()

                .orElse(null);

        if (triggerId == null) {

            throw new CanvasValidationException(

                    "Rule canvas must include at least one trigger with triggerType=stream",

                    "canvas_validation",

                    List.of(new ErrorResponse.FieldError("canvas.nodes", "missing_stream_trigger"))

            );

        }



        Map<String, List<CanvasEdgeView>> outgoing = new HashMap<>();

        for (Map<String, Object> raw : rawEdges) {

            CanvasEdgeView edge = CanvasEdgeView.fromMap(raw);

            if (edge.source() != null && edge.target() != null) {

                outgoing.computeIfAbsent(edge.source(), ignored -> new java.util.ArrayList<>()).add(edge);

            }

        }



        Set<String> reachable = new HashSet<>();

        Queue<String> queue = new ArrayDeque<>();

        queue.add(triggerId);

        boolean hasAction = false;

        while (!queue.isEmpty()) {

            String nodeId = queue.poll();

            if (!reachable.add(nodeId)) {

                continue;

            }

            CanvasNodeView node = nodesById.get(nodeId);

            if (node != null && ACTION_TYPES.contains(node.type())) {

                hasAction = true;

                validateNotifyNode(node);

            }

            for (CanvasEdgeView edge : outgoing.getOrDefault(nodeId, List.of())) {

                queue.add(edge.target());

            }

        }



        if (!hasAction) {

            throw new CanvasValidationException(

                    "Rule canvas must include at least one action block (dedup, threshold, correlation, notify, or push) reachable from the stream trigger",

                    "canvas_validation",

                    List.of(new ErrorResponse.FieldError("canvas.nodes", "missing_action_block"))

            );

        }



        List<String> orphanIds = nodesById.keySet().stream()

                .filter(id -> !reachable.contains(id))

                .toList();

        if (!orphanIds.isEmpty()) {

            throw new CanvasValidationException(

                    "Rule canvas contains nodes not reachable from the stream trigger",

                    "canvas_validation",

                    List.of(new ErrorResponse.FieldError("canvas.nodes", "orphan_nodes"))

            );

        }

    }



    private void validateNotifyNode(CanvasNodeView node) {

        if (!"notify".equals(node.type())) {

            return;

        }

        String channel = node.config().get("channel");

        if (channel == null || channel.isBlank()) {

            throw new CanvasValidationException(

                    "Notify block requires channel (telegram or email)",

                    "canvas_validation",

                    List.of(new ErrorResponse.FieldError("canvas.nodes", "notify_missing_channel"))

            );

        }

        if ("email".equals(channel)) {

            String email = node.config().get("emailAddress");

            if (email == null || email.isBlank() || !isValidEmail(email)) {

                throw new CanvasValidationException(

                        "Notify block with email channel requires a valid emailAddress",

                        "canvas_validation",

                        List.of(new ErrorResponse.FieldError("canvas.nodes", "notify_invalid_email"))

                );

            }

        }

    }



    private static boolean isValidEmail(String email) {

        int at = email.indexOf('@');

        return at > 0 && at < email.length() - 1 && email.contains(".");

    }

}


