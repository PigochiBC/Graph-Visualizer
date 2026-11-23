import java.util.List;

interface Graph{
    void addNode(Object node);
    void addEdge(Object source, Object target);
    List<Object> getNodes();
    List<Object> getNeighbors(Object node);
}
