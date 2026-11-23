import java.util.Arrays;
public class Main {
    //used for test cases
    public static void main(String[] args) {
        MTXConverterSparse graph = new MTXConverterSparse("TestCases/test1.mtx");
        System.out.println("Neighbors array: " + Arrays.toString(graph.getNeighbors()));
        System.out.println("Row pointers array: " + Arrays.toString(graph.getRowPointers()));
    }
}
