import java.util.List;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
public class MTXConverter implements Graph, Cloneable {

    private boolean isSparse = true;
    //Store sparse graph in 2 1d arrays to save lookup time and space.
    private int[] sparseNeighbors;
    private int[] rowPointers;

    private int wordsPerRow = 1;
    private long[] denseNeighbors;

    public MTXConverter(String filePath){
        this.convertToMTX(filePath);
    }
    private MTXConverter(int[] sparseNeighbors, int[] rowPointers, boolean isSparse){
        this.sparseNeighbors = sparseNeighbors;
        this.rowPointers = rowPointers;
        this.isSparse = isSparse;
    }
    private MTXConverter(long[] denseNeighbors, int wordsPerRow, boolean isSparse){
        this.denseNeighbors = denseNeighbors;
        this.wordsPerRow = wordsPerRow;
        this.isSparse = isSparse;
    }
    private MTXConverter convertToMTX(String filePath){
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
            double density = (numRows > 1) ? (2.0 * validEdges) / ((long) numRows * (numCols - 1)) : 0.0;
            if (numRows >= 128 && density > 0.20) {
                isSparse = false;
                return buildDenseGraph(validEdges, numRows, edgeU, edgeV);
            }
            isSparse = true;
            return buildSparseGraph(validEdges, numRows, edgeU, edgeV, rowPointers);
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
        
    }
    
    private MTXConverter buildSparseGraph(int validEdges, int numRows, int[] edgeU, int[] edgeV, int[] rowPointers) {
        //convert rowPointers to cumulative sum
        for(int i = 1; i < rowPointers.length; i++){
            rowPointers[i] += rowPointers[i - 1];
        }
        int[] cursors = rowPointers.clone();
        sparseNeighbors = new int[validEdges * 2];

        //populate neighbors array and sort each row
        for(int i = 0; i < validEdges; i++){
            int u = edgeU[i];
            int v = edgeV[i];

            sparseNeighbors[cursors[u]++] = v;
            sparseNeighbors [cursors[v]++] = u;  
        }

        for(int i = 0; i < numRows; i++){
            Arrays.sort(sparseNeighbors, rowPointers[i], rowPointers[i + 1]);
        }
        return this;
    }
    private MTXConverter buildDenseGraph(int validEdges, int numRows, int[] edgeU, int[] edgeV) {
        rowPointers = null;
        wordsPerRow = (numRows + 63) / 64; // words per row = num edges / num vertices
        denseNeighbors = new long[wordsPerRow * numRows]; 

        for(int i = 0; i < validEdges; i++){ //set bits in denseNeighbors for each edge 1 = neighbor, 0 = not neighbor
            int u = edgeU[i];
            int v = edgeV[i];

            denseNeighbors[u * wordsPerRow + v / 64] |= (1L << (v % 64));//set bit for u's neighbor v
            denseNeighbors[v * wordsPerRow + u / 64] |= (1L << (u % 64));//set bit for v's neighbor u
        }

        return this;
    }
    
    public MTXConverter permute(int[] vertexOrder){
        if(vertexOrder.length != (isSparse ? rowPointers.length - 1 : denseNeighbors.length / wordsPerRow)){
            throw new IllegalArgumentException("Vertex order length must match the number of vertices in the graph.");
        }
        if(isSparse){
            return optimizeSparse(vertexOrder);
        }else{
            return optimizeDense(vertexOrder);
        }

    }
    private MTXConverter optimizeSparse(int[] vertexOrder){ //reorder vertices of sparse graph to make things like bandwidth easier
        int rowCount = rowPointers.length - 1;
        if(vertexOrder.length != rowCount) {
            throw new IllegalArgumentException("Vertex order length must match the number of rows in the graph.");
        }
        int[] originalToNew = new int[rowCount];

        //create mapping from original vertex index to new vertex index based on vertexOrder
        for(int newIndex = 0; newIndex < vertexOrder.length; newIndex++){
            originalToNew[vertexOrder[newIndex]] = newIndex;
        }

        int[] newRowPointers = new int[rowCount + 1];
        int[] newSparseNeighbors = new int[sparseNeighbors.length];

        //calculate new rowPointers based on vertexOrder
        for(int newId = 0; newId < rowCount; newId++){
            int oldId = vertexOrder[newId];
            int degree = rowPointers[oldId + 1] - rowPointers[oldId];
            newRowPointers[newId + 1] = newRowPointers[newId] + degree;  
        }

        //copy relabeled neighbors to newSparseNeighbors array and sort each row
        for(int newId = 0; newId < rowCount; newId++){
            int oldId = vertexOrder[newId];
            int start = rowPointers[oldId];
            int end = rowPointers[oldId + 1];
            int newStart = newRowPointers[newId];

            for(int j = start; j < end; j++){    
                int oldNeighbor = sparseNeighbors[j];
                int newNeighbor = originalToNew[oldNeighbor];
                newSparseNeighbors[newStart++] = newNeighbor;
            }
            Arrays.sort(newSparseNeighbors, newRowPointers[newId], newRowPointers[newId + 1]);
        }

        return new MTXConverter(newSparseNeighbors, newRowPointers, true);

    }
    private MTXConverter optimizeDense(int[] vertexOrder){ //reorder vertices of dense graph to make things like bandwidth easier
        int[] originalToNew = new int[vertexOrder.length];
        for(int newIndex = 0; newIndex < vertexOrder.length; newIndex++){
            originalToNew[vertexOrder[newIndex]] = newIndex;
        }

        long[] newDenseNeighbors = new long[denseNeighbors.length];
        for(int oldU= 0; oldU < vertexOrder.length; oldU++){
            int newU = originalToNew[oldU];
            int oldRowStart = oldU * wordsPerRow;
            int newRowStart = newU * wordsPerRow;

            for(int word = 0; word < wordsPerRow; word++){
                long oldWord = denseNeighbors[oldRowStart + word];
                int baseCol = word << 6; // base column index for this word (word * 64)
                while (oldWord != 0L) {
                    int bitPos = Long.numberOfTrailingZeros(oldWord);
                    int oldV = baseCol + bitPos;
                    int newV = originalToNew[oldV];

                    // Set bit newV inside row newU
                    newDenseNeighbors[newRowStart + (newV >>> 6)] |= (1L << (newV & 63));

                    // Clear the lowest set bit
                    oldWord &= (oldWord - 1);
                }
            }
        }
        return new MTXConverter(newDenseNeighbors, wordsPerRow, false);
    }
    
    public int[] getSparseNeighbors() {
        return sparseNeighbors;
    }
    public long[] getDenseNeighbors() {
        return denseNeighbors;
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