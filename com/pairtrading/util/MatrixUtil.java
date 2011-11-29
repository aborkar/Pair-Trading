package com.pairtrading.util;

import org.jblas.DoubleMatrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Sara
 * Date: 11/24/11
 * Time: 10:13 AM
 * To change this template use File | Settings | File Templates.
 */
public class MatrixUtil {


    public static double[][] get2DMatrix(DoubleMatrix x){
        double[][] retMat = new double[x.rows][x.columns];
        for(int i = 0; i <  x.rows;i++)
            for(int j = 0; j < x.columns;j++){
                retMat[i][j] = x.get(i,j);
            }
        return retMat;
    }

    public static double[] get1DMatrix(DoubleMatrix x){
        double[] retMat = new double[x.rows];
        for(int i = 0; i <  x.rows;i++)
            //for(int j = 0; j < x.columns;j++){
                retMat[i] = x.get(i,0);
            //}
        return retMat;
    }

    public static ArrayList<Integer> ones(DoubleMatrix pairsVector, ArrayList<Integer> arr){
        ArrayList<Integer> arr2 = new ArrayList<Integer>();
        for(int i =0; i < arr.size();i++){
            arr2.add(i,1);
        }
        for(int j = arr2.size(); j < pairsVector.length ; j++){
            arr2.add(0);
        }

        return arr2;
    }

    public static void extractSubMatrix(DoubleMatrix source, DoubleMatrix destination, int rowIndices[],
                                        int columnIndices[]){
        for(int i= rowIndices[0],i_ = 0 ; i < rowIndices[1] ;i++,i_++){
            for(int j= columnIndices[0],j_=0; j < columnIndices[1] ;j++,j_++){
                destination.put(i_,j_,source.get(i,j));
            }
        }

    }

    public static void extractSubMatrix(DoubleMatrix source, DoubleMatrix destination, int rowIndices[],
                                        int columnIndices[], int destStartRow, int destStartColumn){
        for(int i= rowIndices[0],i_ = destStartRow ; i < rowIndices[1] ;i++,i_++){
            for(int j= columnIndices[0],j_=destStartColumn; j < columnIndices[1] ;j++,j_++){
                destination.put(i_,j_,source.get(i,j));
            }
        }
    }

    public static void extractSubMatrix(DoubleMatrix source, double[] destination, int rowIndices[],
                                        int columnIndices[], int destStartRow, int destStartColumn){
        for(int i= rowIndices[0],i_ = destStartRow ; i < rowIndices[1] ;i++,i_++){
//            for(int j= columnIndices[0],j_=destStartColumn; j < columnIndices[1] ;j++,j_++){
                destination[i_]= source.get(i,0);
//            }
        }
    }

    public static double columnMeans(double[] localVector, List<Integer> skipList){
        double cumSum = 0.0; int cumSumCount=0;
        for(int i=0;i<localVector.length;i++){
            if(skipList.contains(i)){
                continue;
            }
            cumSum+=localVector[i];
            cumSumCount++;
        }
        return cumSum/cumSumCount;
    }

    public static DoubleMatrix getValueVector(int rows, double value){
        double[] vector = new double[rows];
        for(int i=0 ; i < rows; i++){
            vector[i]=value;
        }
        return new DoubleMatrix(vector);
    }

    public static double[] addOnes(double[] vector){
        for(int i=0 ; i < vector.length; i++){
            vector[i]+=1;
        }
        return vector;
    }
}
