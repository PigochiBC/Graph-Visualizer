import java.util.List;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
public class MTXConverterSparse implements Graph {

    //Store graph in 2 1d arrays to save lookup time and space.

    private int[] neighbors;
    private int[] rowPointers;

    public MTXConverterSparse(String filePath){

        this.convertToMTX(filePath);
    }

    private MTXConverterSparse convertToMTX(String filePath){
        try(BufferedReader reader = new BufferedReader(new FileReader(filePath))){
            int numRows = 0, numCols = 0, numEdges = 0;
            String line;
            //parse header
            while((line = reader.readLine()) != null){
                line = line.trim();
                if (line.isEmpty() || line.startsWith("%")){
                    continue;
                }else{
                    String[] dimensions = line.trim().split("\\s+");
                    numRows = Integer.parseInt(dimensions[0]);
                    numCols = Integer.parseInt(dimensions[1]);
                    numEdges = Integer.parseInt(dimensions[2]);
                    break;
                }
            }

            //error check for valid header / graph
            if(line == null){
                throw new IllegalArgumentException("File does not contain a valid header.");
            }
            if(numRows != numCols){
                throw new IllegalArgumentException("Graph must be square.");
            }

            //create arrays to store edges and edge counts
            int[] edgeU = new int[numEdges];
            int[] edgeV = new int[numEdges];
            rowPointers = new int[numRows + 1];
            int validEdges = 0;
            //go through rest of file and store edges in arrays
            while((line = reader.readLine()) != null){
                line = line.trim();
                if (line.isEmpty() || line.startsWith("%")){
                    continue;
                }else{
                    String[] edge = line.split("\\s+");
                    int u = Integer.parseInt(edge[0]) - 1; //convert to 0-based index
                    int v = Integer.parseInt(edge[1]) - 1; //convert to 0-based index

                    if(u == v)continue;
                    
                    edgeU[validEdges] = u;
                    edgeV[validEdges] = v;

                    validEdges++;

                    rowPointers[u + 1]++;
                    rowPointers[v + 1]++;
                }
            }
            return buildSparseGraph(validEdges, numRows, edgeU, edgeV, rowPointers);
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
        
    }

    private MTXConverterSparse buildSparseGraph(int validEdges, int numRows, int[] edgeU, int[] edgeV, int[] rowPointers) {
        //convert rowPointers to cumulative sum
        for(int i = 1; i < rowPointers.length; i++){
            rowPointers[i] += rowPointers[i - 1];
        }
        int[] cursors = rowPointers.clone();
        neighbors = new int[validEdges * 2];

        //populate neighbors array and sort each row
        for(int i = 0; i < validEdges; i++){
            int u = edgeU[i];
            int v = edgeV[i];

            neighbors[cursors[u]++] = v;
            neighbors[cursors[v]++] = u;  
        }

        for(int i = 0; i < numRows; i++){
            Arrays.sort(neighbors, rowPointers[i], rowPointers[i + 1]);
        }
        return this;
    }

    public int[] getNeighbors() {
        return neighbors;
    }
    public int[] getRowPointers() {
        return rowPointers;
    }
    @Override
    public void addNode(Object node) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addNode'");
    }

    @Override
    public void addEdge(Object source, Object target) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addEdge'");
    }

    @Override
    public List<Object> getNodes() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNodes'");
    }

    @Override
    public List<Object> getNeighbors(Object node) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNeighbors'");
    }
    
}