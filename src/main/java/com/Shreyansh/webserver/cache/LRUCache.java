package com.Shreyansh.webserver.cache;

import java.util.concurrent.ConcurrentHashMap;

public class LRUCache {
    private int capacity;
    private Node head;
    private Node tail;
    private ConcurrentHashMap<String, Node> map;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new ConcurrentHashMap<String, Node>(capacity);
        head = new Node("", null, "");
        tail = new Node("", null, "");
        head.next = tail;
        tail.prev = head;
    }
    private static class Node {
        public String key;
        public byte[] value;
        public String contentType;
        public Node prev;
        public Node next;

        public Node(String key, byte[] value, String contentType) {
            this.key = key;
            this.value = value;
            this.contentType = contentType;
        }
    }

    public static class cachedFile {
        public byte[] data;
        public String contentType;

        public cachedFile(byte[] data, String contentType) {
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
        Node temp = map.remove(node.key);
        temp.prev.next = temp.next;
        temp.next.prev = temp.prev;
    }

    public synchronized cachedFile get(String key) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            cachedFile cachedFile = new cachedFile(node.value, node.contentType);
            remove(node);
            insertToFront(node);
            return cachedFile;
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
