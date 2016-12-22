package utils;


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by bozyurt on 6/15/16.
 */
public class Node<T> {
    Node<T> parent;
    List<Node<T>> children = new LinkedList<Node<T>>();
    T payload;

    public Node(T payload, Node<T> parent) {
        this.parent = parent;
        this.payload = payload;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public Node<T> addChild(T payload) {
        Node<T> child = new Node(payload, this);
        children.add(child);
        return child;
    }

    public Node<T> getParent() {
        return parent;
    }

    public List<Node<T>> getChildren() {
        return children;
    }

    public T getPayload() {
        return payload;
    }

    public static <T> List<Node<T>> getLeafNodes(Node<T> parentNode) {
        List<Node<T>> list = new ArrayList<Node<T>>(10);
        collectLeafNodes(parentNode, list);
        return list;
    }

    static <T> void collectLeafNodes(Node<T> parentNode, List<Node<T>> list) {
        if (parentNode.hasChildren()) {
            for (Node<T> child : parentNode.getChildren()) {
                collectLeafNodes(child, list);
            }
        } else {
            list.add(parentNode);
        }
    }
}
