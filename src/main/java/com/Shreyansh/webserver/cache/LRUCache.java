package com.Shreyansh.webserver.cache;

import java.util.HashMap;
import java.util.Map;

public class LRUCache {
    private final int capacity;
    private final Node head;
    private final Node tail;
    private final Map<String, Node> map;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);
        head = new Node("", null, "");
        tail = new Node("", null, "");
        head.next = tail;
        tail.prev = head;
    }

    private static class Node {
        String key;
        byte[] value;
        String contentType;
        Node prev;
        Node next;

        Node(String key, byte[] value, String contentType) {
            this.key = key;
            this.value = value;
            this.contentType = contentType;
        }
    }

    public static class CachedFile {
        public final byte[] data;
        public final String contentType;

        public CachedFile(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }
    }

    private void insertToFront(Node node) {
        map.put(node.key, node);
        head.next.prev = node;
        node.next = head.next;
        head.next = node;
        node.prev = head;
    }

    private void remove(Node node) {
        map.remove(node.key);
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    public synchronized CachedFile get(String key) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            CachedFile result = new CachedFile(node.value, node.contentType);
            remove(node);
            insertToFront(node);
            return result;
        }
        return null;
    }

    public synchronized void put(String key, byte[] value, String contentType) {
        if (map.containsKey(key)) {
            remove(map.get(key));
        }
        if (map.size() == capacity) {
            remove(tail.prev);
        }
        Node node = new Node(key, value, contentType);
        insertToFront(node);
    }

    public synchronized void remove(String key) {
        if (map.containsKey(key)) {
            remove(map.get(key));
        }
    }
}
