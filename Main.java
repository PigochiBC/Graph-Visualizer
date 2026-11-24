import java.util.Arrays;
public class Main {
    //used for test cases
    public static void main(String[] args) {
        MTXConverter graph = new MTXConverter("TestCases/test1.mtx");
        System.out.println("Sparse neighbors array: " + Arrays.toString(graph.getSparseNeighbors()));
        System.out.println("Row pointers array: " + Arrays.toString(graph.getRowPointers()));
    }
}
