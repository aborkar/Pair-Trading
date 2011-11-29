package com.pairtrading;

import com.pairtrading.util.MathUtil;
import com.pairtrading.util.MatrixUtil;
import org.jblas.DoubleMatrix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: Anirudh
 * Date: 9/12/11
 * Time: 8:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class PairTrading {

    public static Logger log = Logger.getLogger(PairTrading.class.getName());
    DoubleMatrix dailyTradePrices =null;
    Double capital = 100000D;
    Double tradeCommission  = 10D;
    private static final int CORRELATION_METHODOLOGY_MIN_SQRD_DIST=1;
    private static final int CORRELATION_METHODOLOGY_LINEAR_REGRESSION=2;

    int pairingInterval = 400;int tradingWindow=401;int pairComputingFrequency = 5;double tradingThreshold=1.5;
    int maxPeriod = 5; int correlationStrategy=CORRELATION_METHODOLOGY_MIN_SQRD_DIST;
    int numberOfTrades=0;
    ArrayList<Trade> tradesContainer;
    ArrayList<DoubleMatrix> pairsSet;
    DoubleMatrix pairingSubMatrix,pairsVector,distMatrix,anyPos;
    ArrayList<Double> longPosBuys, shortPosSells,longPosSells,shortPosBuys;


    public PairTrading(){
        //load the matrix
        try{
            dailyTradePrices = DoubleMatrix.loadAsciiFile("C:\\Users\\Sara\\my_data4.txt");
            //dailyTradePrices = loadFile("C:\\Users\\Sara\\Stocks394.csv");//DoubleMatrix.loadAsciiFile("C:\\Users\\Sara\\Stocks394.txt");
        }catch(Exception ioe){
            ioe.printStackTrace();
        }
        tradesContainer = new ArrayList<Trade>();
        pairsSet = new ArrayList<DoubleMatrix>();
        pairingSubMatrix=new DoubleMatrix(pairingInterval, dailyTradePrices.columns);
        pairsVector=new DoubleMatrix();
        distMatrix = new DoubleMatrix(dailyTradePrices.rows - tradingWindow + 1, dailyTradePrices.columns);
        anyPos = new DoubleMatrix(dailyTradePrices.rows - pairingInterval, dailyTradePrices.columns);
        longPosBuys=new ArrayList<Double>(); shortPosSells=new ArrayList<Double>();
        longPosSells=new ArrayList<Double>();shortPosBuys=new ArrayList<Double>();
    }

    public void run(){
        for(int row=0; row< dailyTradePrices.rows - pairingInterval ; row++){
            int[] rowIndices = {tradingWindow-pairingInterval + row-1, row+tradingWindow-1}, columnIndices = {0, dailyTradePrices.columns};
            //for each row, take the submatrix equivalent to the pairing interval,
            // compute the pairs
            // store the pairs in a pairs array indexxed against the day
            // now start examining each row for the trading event/signal

            MatrixUtil.extractSubMatrix(dailyTradePrices, pairingSubMatrix, rowIndices, columnIndices);
            //normalize this matrix
            pairingSubMatrix = normalizeSB(pairingSubMatrix);

            if(row==0 || ((1+row)%pairComputingFrequency)==0){
                pairsVector = findPairs(pairingSubMatrix, correlationStrategy);
            }

            pairsSet.add(pairsVector);

            for(int col=0; col< pairingSubMatrix.columns ; col++){
                if(pairsVector.get(col, 0) < 0){
                    distMatrix.put(row,col, Double.NEGATIVE_INFINITY);
                    continue;
                }
                distMatrix.put(row,col,pairingSubMatrix.get(pairingSubMatrix.rows-1,col)-pairingSubMatrix.get(pairingSubMatrix.rows-1,(int)pairsVector.get(col,0)));
            }

            Integer[] arr = checkForTradingSignals(distMatrix,row,tradingThreshold, pairsVector);

            if(arr.length>0){
                int col;
                for(col=0;col< arr.length ;col++){
                    if(pairsVector.get(arr[col],0) < 0)
                        continue;
                    if(row==0 || anyPos.get(row-1,arr[col])==0 ){
                        //set up the trade
                        log.info("Trading for pair: ["+ arr[col] + ","+ pairsVector.get(arr[col],0) + "] on " + row + " day " + ",obs num. " + (row + tradingWindow - 1) + " ..trade num: " + (tradesContainer.size() + 1));
                        //log.info("Trading indexes :" + Arrays.asList(arr).toString());
                        Trade trade = new Trade();
                        if(distMatrix.get(row,arr[col]) > 0){
                            trade.setPair(trade.new Pair(arr[col],(int)pairsVector.get(arr[col],0), (int)pairsVector.get(arr[col],0),arr[col]));
                        }else{
                            trade.setPair(trade.new Pair(arr[col],(int)pairsVector.get(arr[col],0) ,arr[col], (int)pairsVector.get(arr[col],0)));
                        }
                        trade.setObservationNumber(row + tradingWindow-1);
                        tradesContainer.add(trade);
                    }
                    anyPos.put(row, arr[col],1);
                    anyPos.put(row, (int)pairsVector.get(arr[col],0),1);
                }
            }
        }

        checkPositions();
        log.info("longPosBuys" + longPosBuys);
        log.info("shortPosSells" + shortPosSells);

        computeProfits();
    }

    private DoubleMatrix resizeDistanceMatrix(DoubleMatrix distMatrix){
        DoubleMatrix oldDistMatrix = distMatrix.dup();
        distMatrix.resize(distMatrix.rows + tradingWindow - 1, distMatrix.columns);
        for(int i =0;i< oldDistMatrix.rows ; i++){
            for(int j =0;j < oldDistMatrix.columns ; j++){
                distMatrix.put(i+tradingWindow-1,j,oldDistMatrix.get(i,j));
            }
        }
        return distMatrix;
    }

    public void checkPositions(){
        int j = 0;
        distMatrix = resizeDistanceMatrix(distMatrix);
        for(Trade trade:tradesContainer){
            j=j+1;
            log.info("Completed " + j + " trades..");
            longPosBuys.add(dailyTradePrices.get(trade.getObservationNumber(), trade.getPair().getLongSecIndex()));
            shortPosSells.add(dailyTradePrices.get(trade.getObservationNumber(), trade.getPair().getShortSecIndex()));

            int i=0;
            while(true){
                i=i+1;

                //conditions to close trades
                // a) if the position converges
                // b) if the position is held beyond the observations horizon
                // c) if the pair has changed in the following pairs-flipping cycle
                // d) if the max holding period of a trade has been exceeded
                if(Math.abs(distMatrix.get(trade.getObservationNumber()+i,trade.getPair().firstSecurityIndex)) < tradingThreshold){
                    //close the position
                    break;
                }

                if(i > maxPeriod){
                    break;
                }

                if(i+trade.observationNumber > tradesContainer.size()){
                    break;
                }

                int firstSecPair = trade.getPair().firstSecurityIndex;
                int secondSecPair = trade.getPair().secondSecurityIndex;
                int observIndex = trade.getObservationNumber() - tradingWindow + 1 + i;

                if(pairsSet.get(observIndex).get(firstSecPair,0)!=secondSecPair){
                    break;
                }

            }
            longPosSells.add(dailyTradePrices.get(trade.getObservationNumber() + i, trade.getPair().getLongSecIndex()));
            shortPosBuys.add(dailyTradePrices.get(trade.getObservationNumber() + i, trade.getPair().getShortSecIndex()));
        }

    }

    public void computeProfits(){
/**
 *
 * ProfitLong =Capital.*(longPos_Sell-longPos_Buy)./longPos_Buy-C;
ProfitShort=Capital.*(shortPos_Sell-shortPos_Buy)./shortPos_Sell-C;
ProfitComb=ProfitLong+ProfitShort;

profitOut.totalProfitLong =sum(ProfitLong);
profitOut.totalProfitShort=sum(ProfitShort);
profitOut.totalProfitComb =sum(ProfitComb);
 *
 *
 */
        double netPos=0, profitLong=0;
        for(int i = 0; i < longPosBuys.size();i++){
            netPos=+(longPosSells.get(i) - longPosBuys.get(i))/(longPosBuys.get(i)-tradeCommission);
            profitLong = capital * netPos;
        }



    }

    public DoubleMatrix normalize(DoubleMatrix mat){
        DoubleMatrix ret  = new DoubleMatrix(mat.rows, mat.columns);
        DoubleMatrix ones = DoubleMatrix.ones(mat.rows);
        DoubleMatrix localVector = new DoubleMatrix(mat.rows,1);
        DoubleMatrix temp  = new DoubleMatrix(mat.rows-1, 1), temp1 = new DoubleMatrix(mat.rows-1,1);

        for(int col=0; col < mat.columns ; col++){
            MatrixUtil.extractSubMatrix(mat, temp, new int[]{1, mat.rows}, new int[]{col, col + 1});
            MatrixUtil.extractSubMatrix(mat, temp1, new int[]{0, mat.rows - 1}, new int[]{col, col + 1});

            temp = temp.sub(temp1);
            double[][] tempMat = MatrixUtil.get2DMatrix(temp);
            for(int i=0;i< tempMat.length ;i++){
                for(int j=0;j<tempMat[i].length;j++){
                    //log.info("row " + row + " col = " + col);
                    temp.put(i,j,tempMat[i][j]/temp1.get(i,j));
                }
            }
            //log.info(temp.toString());
            localVector.put(0,0,0.0);
            //localVector.put(buildIndex(1, temp.rows+1),0,temp);
            MatrixUtil.extractSubMatrix(temp, localVector, new int[]{0, temp.rows}, new int[]{0, 1}, 1, 0);
            localVector = localVector.add(ones);
            MathUtil.cumulativeProduct(localVector);
            //ret.put(0,col,0.0);
            //ret.put(buildIndex(1, temp.rows+1),col,temp);
            //log.info(localVector.toString());
            double mean = localVector.columnMeans().get(0,0);
            //now find the std deviation
            double standardDeviation = MathUtil.standardDeviation(localVector, mean);
            //normalize again
            for(int row=0; row<localVector.rows;row++)
                localVector.put(row,0, (localVector.get(row,0) - mean)/standardDeviation);

             //now copy this vector into the return matrix
            ret.putColumn(col, localVector);
        }

        return ret;
    }


    private List<Integer> createSkipColumnList(DoubleMatrix mat){
        List<Integer> skipList = new ArrayList<Integer>();
        DoubleMatrix colMatrix;
        for(int i=0; i< mat.columns;i++){
            colMatrix = mat.getColumn(i);
            if(colMatrix.min()<0)
                skipList.add(i);
        }

        return skipList;
    }

    public DoubleMatrix normalizeSB(DoubleMatrix mat){
        List<Integer> skipList = createSkipColumnList(mat);

        DoubleMatrix ret  = new DoubleMatrix(mat.rows, mat.columns);
        DoubleMatrix ones = DoubleMatrix.ones(mat.rows);
        //DoubleMatrix localVector = new DoubleMatrix(mat.rows,1);
        double[] localVector  = new double[mat.rows];
        DoubleMatrix temp  = new DoubleMatrix(mat.rows-1, 1), temp1 = new DoubleMatrix(mat.rows-1,1);

        for(int col=0; col < mat.columns ; col++){
            if(skipList.contains(col)){
             //now copy this vector into the return matrix
                ret.putColumn(col, MatrixUtil.getValueVector(mat.rows, Double.NaN));
                continue;
            }
            MatrixUtil.extractSubMatrix(mat, temp, new int[]{1, mat.rows}, new int[]{col, col + 1});
            MatrixUtil.extractSubMatrix(mat, temp1, new int[]{0, mat.rows - 1}, new int[]{col, col + 1});

            temp = temp.sub(temp1);
            double[][] tempMat = MatrixUtil.get2DMatrix(temp);
            for(int i=0;i< tempMat.length ;i++){
                //skip column if it contains -ve values
                for(int j=0;j<tempMat[i].length;j++){
                    //log.info("row " + row + " col = " + col);
                    temp.put(i,j,tempMat[i][j]/temp1.get(i,j));
                }
            }
            //log.info(temp.toString());
            localVector[0] = 0.0;
            //localVector.put(buildIndex(1, temp.rows+1),0,temp);
            MatrixUtil.extractSubMatrix(temp, localVector, new int[]{0, temp.rows}, new int[]{0, 1}, 1, 0);
            localVector = MatrixUtil.addOnes(localVector);
            MathUtil.cumulativeProduct(localVector, skipList);
            //ret.put(0,col,0.0);
            //ret.put(buildIndex(1, temp.rows+1),col,temp);
            //log.info(localVector.toString());
            double mean = MatrixUtil.columnMeans(localVector, skipList);
            //now find the std deviation
            double standardDeviation = MathUtil.standardDeviation(localVector, mean, skipList);
            //normalize again
            for(int row=0; row<localVector.length;row++){
                localVector[row] = (localVector[row] - mean)/standardDeviation;
            }

             //now copy this vector into the return matrix
            ret.putColumn(col, new DoubleMatrix(localVector));
        }

        return ret;
    }

    private  DoubleMatrix findPairs(DoubleMatrix mat, int correlationStrategy){
        DoubleMatrix pairsCovarMat = new DoubleMatrix(mat.columns, mat.columns);
        double correlationCoefficient;

        for(int i =0; i < pairsCovarMat.rows;i++){
            for(int j =0; j < i;j++ ){

                correlationCoefficient = correlationStrategy==CORRELATION_METHODOLOGY_MIN_SQRD_DIST?findMeanSquaredDistance(mat, i, j): MathUtil.computeRegressionCoefficient(MatrixUtil.get1DMatrix(mat.getColumn(i)), MatrixUtil.get1DMatrix(mat.getColumn(j)));
                pairsCovarMat.put(i,j,correlationCoefficient);
                pairsCovarMat.put(j,i,pairsCovarMat.get(i,j));
            }
            pairsCovarMat.put(i,i,Double.NaN);
        }

        return findMostCorrelation(pairsCovarMat);
    }

    private double findMeanSquaredDistance(DoubleMatrix mat, int i, int j) {
        DoubleMatrix temp = new DoubleMatrix(mat.rows,1);
        for(int k =0; k < mat.rows;k++){
            temp.put(k,0,Math.pow(mat.get(k,i) - mat.get(k,j),2));
        }
        return temp.cumulativeSum().get(temp.rows - 1, 0);
    }

    private DoubleMatrix findMostCorrelation(DoubleMatrix mat){
        DoubleMatrix corrVector, retVectorCorrIndexes = MatrixUtil.getValueVector(mat.rows,-1);
        corrVector = mat.rowMins();
        for(int row=0; row < mat.rows;row++){
            for(int col = 0; col < mat.columns;col++){
                if(corrVector.get(row,0) == mat.get(row,col))
                    retVectorCorrIndexes.put(row,0,col);
            }
        }
        log.info("Pairs " + displayPairs(retVectorCorrIndexes));
        return retVectorCorrIndexes;
    }

    private String displayPairs(DoubleMatrix retVectorCorrIndexes){
        StringBuffer sb = new StringBuffer();
        for(int i=0;i< retVectorCorrIndexes.rows;i++){
            sb.append("[" ).append(i).append(" ").append(retVectorCorrIndexes.get(i,0)).append("]");
        }
        return sb.toString();
    }

    public static void main(String[] args){

        PairTrading rand = new PairTrading();
        rand.run();
    }

    private Integer[] checkForTradingSignals(DoubleMatrix distanceMatrix, int currRow, double tradingThreshold, DoubleMatrix pairsVector){
        ArrayList<Integer> arr = new ArrayList<Integer>();
        for(int i=0; i < distanceMatrix.columns;i++){
            if(Math.abs(distanceMatrix.get(currRow, i))> tradingThreshold)
                arr.add(i);
        }

        arr = resolveDuplicates(arr, pairsVector);

        return arr.toArray(new Integer[arr.size()]);
    }


     private ArrayList<Integer> resolveDuplicates(ArrayList<Integer> arr, DoubleMatrix pairsVector){
        ArrayList<Integer> otherIdx = MatrixUtil.ones(pairsVector, arr);
        for(int i=0; i < pairsVector.length;i++){
            if(pairsVector.get(i, 0) < 0){
                continue;
            }
            if(i == (int)pairsVector.get((int)pairsVector.get(i,0),0)){
                for(int j=0; j < arr.size();j++){
                    Integer idx = arr.get(j);
                    if(idx==Integer.MAX_VALUE)
                        continue;
                    if(pairsVector.get(idx, 0) < 0){
                        continue;
                    }
                    if(idx == (int)pairsVector.get((int)pairsVector.get(idx,0),0)){
                        if(otherIdx.get(j).equals(1)){
                            arr.set(j,Integer.MAX_VALUE);
                            otherIdx.set((int)pairsVector.get(idx,0),0);
                        }
                    }
                }
            }
        }
        return compressArray(arr);
    }

    private ArrayList<Integer> compressArray(ArrayList<Integer> arr){
        ArrayList<Integer> retArray = new ArrayList<Integer>();
        for(Integer intVar:arr){
            if(!(intVar.equals(Integer.MAX_VALUE)))
                retArray.add(intVar);
        }
        return retArray;
    }


    class Trade{
        Pair pair;

        public Pair getPair() {
            return pair;
        }

        public void setPair(Pair pair) {
            Trade.this.pair = pair;
        }

        public int getObservationNumber() {
            return observationNumber;
        }

        public void setObservationNumber(int observationNumber) {
            Trade.this.observationNumber = observationNumber;
        }

        int observationNumber;
        int direction;//long/short;

        Trade(Pair pair, int observationNumber, int direction) {
            Trade.this.pair = pair;
            Trade.this.observationNumber = observationNumber;
        }

        Trade(){
            pair=null;
        }

        class Pair{
            int firstSecurityIndex, secondSecurityIndex, longSecIndex, shortSecIndex;
            double longSecBuyPx,longSecSellPx,shortSecBuyPx,shortSecSellPx;

            public int getLongSecIndex() {
                return longSecIndex;
            }

            public int getShortSecIndex() {
                return shortSecIndex;
            }

            Pair(int firstSecurityIndex, int secondSecurityIndex,int longSecIndex, int shortSecIndex){
                Pair.this.firstSecurityIndex=firstSecurityIndex;
                Pair.this.secondSecurityIndex=secondSecurityIndex;
                Pair.this.longSecIndex=longSecIndex;
                Pair.this.shortSecIndex=shortSecIndex;
            }

            public void setPosOpenPxs(double longSecBuyPx, double shortSecSellPx){
                Pair.this.shortSecSellPx=shortSecSellPx;
                Pair.this.longSecBuyPx=longSecBuyPx;
            }

            public void setPosClosePxs(double longSecSellPx, double shortSecBuyPx){
                Pair.this.shortSecSellPx=shortSecSellPx;
                Pair.this.longSecBuyPx=longSecBuyPx;
            }
        }

    }

    private DoubleMatrix loadFile(String fileName) throws IOException{
        double numbers[][] = new double[2515][380];

        BufferedReader bufRdr = null;
        File file = new File(fileName);
        try{

            bufRdr  = new BufferedReader(new FileReader(file));
            String line = null;
            int row = 0;
            int col = 0;

            //read each line of text file
            while((line = bufRdr.readLine()) != null)
            {
                col=0;
                StringTokenizer st = new StringTokenizer(line,",");
                while (st.hasMoreTokens())
                {
                    //get next token and store it in the array
                    numbers[row][col] = Double.valueOf(st.nextToken()).doubleValue();
                    col++;
                }
                row++;
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{

            //close the file
            bufRdr.close();
        }
        return new DoubleMatrix(numbers);

    }

}
