package com.Shreyansh.webserver.routing;

import com.Shreyansh.webserver.http.HttpMethod;
import java.util.HashMap;
import java.util.Map;

public class TrieNode {
    private final Map<String, TrieNode> children = new HashMap<>();
    private final Map<HttpMethod, RouteHandler> handlers = new HashMap<>();

    public TrieNode() {}

    public Map<String, TrieNode> getChildren() {
        return children;
    }
    public Map<HttpMethod, RouteHandler> getHandlers() {
        return handlers;
    }
}
