package com.pairtrading.util;

import com.pairtrading.PairTrading;
import org.jblas.DoubleMatrix;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Sara
 * Date: 11/24/11
 * Time: 10:34 AM
 * To change this template use File | Settings | File Templates.
 */
public class MathUtil {
    public static void cumulativeProduct(DoubleMatrix matrix){
        for(int row=1;row < matrix.rows; row++ )
            matrix.put(row,0,matrix.get(row-1,0) * matrix.get(row,0));
    }

    public static void cumulativeProduct(double[] localVector, List<Integer> skipList){
        for(int row=1;row < localVector.length; row++ ){
            if(skipList.contains(row)){
                localVector[row]=localVector[row-1];
                continue;
            }
            localVector[row]*=localVector[row-1];
        }
    }

    public static double standardDeviation(DoubleMatrix matrix, double mean){
        double cumulativeSum=0.0;
        for(int row=0;row < matrix.rows; row++ ){
            PairTrading.log.fine("cumulativeSum :" + cumulativeSum + " next term: " + Math.pow((matrix.get(row, 0) - mean), 2));
            cumulativeSum += Math.pow((matrix.get(row, 0) - mean), 2);
        }

        return Math.pow(cumulativeSum/matrix.rows,0.5);
    }


    public static double standardDeviation(double[] localVector, double mean, List<Integer> skipList){
        double cumulativeSum = 0.0; int countStdDev=0;
        for(int row=0;row < localVector.length; row++ ){
            if(skipList.contains(row)){
                continue;
            }
            //PairTrading.log.fine("cumulativeSum :" + cumulativeSum + " next term: " + Math.pow((matrix.get(row, 0) - mean), 2));
            cumulativeSum += Math.pow((localVector[row] - mean), 2);
            countStdDev++;
        }

        return Math.pow(cumulativeSum/countStdDev,0.5);
    }

    public static double computeRegressionCoefficient(double[] x, double[] y){
        // first pass: read in data, compute xbar and ybar
        double sumx = 0.0, sumy = 0.0, sumx2 = 0.0;
        int n;
        for(n=0; n < x.length; n++) {

            sumx  += x[n];
            sumx2 += x[n] * x[n];
            sumy  += y[n];
        }
        double xbar = sumx / n;
        double ybar = sumy / n;

        // second pass: compute summary statistics
        double xxbar = 0.0, yybar = 0.0, xybar = 0.0;
        for (int i = 0; i < n; i++) {
            xxbar += (x[i] - xbar) * (x[i] - xbar);
            yybar += (y[i] - ybar) * (y[i] - ybar);
            xybar += (x[i] - xbar) * (y[i] - ybar);
        }
        double beta1 = xybar / xxbar;
        double beta0 = ybar - beta1 * xbar;

        return beta1;
    }

}
