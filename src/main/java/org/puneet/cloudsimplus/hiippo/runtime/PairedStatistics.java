package org.puneet.cloudsimplus.hiippo.runtime;

import java.util.*;
import org.apache.commons.math3.distribution.NormalDistribution;

/** Protocol-2 mean inference. Resampling operates on matched replication differences. */
final class PairedStatistics {
    static final int WILD_DRAWS=1_000_000, BCA_DRAWS=10_000;
    private static final NormalDistribution NORMAL=new NormalDistribution();
    private PairedStatistics() {}
    static double mean(double[] x) { double sum=0; for(double v:x) sum+=v; return sum/x.length; }
    static double sd(double[] x) {
        if(x.length<2) return Double.NaN;
        boolean constant=true; for(double v:x) if(v!=x[0]) constant=false;
        if(constant) return 0;
        double m=mean(x),sum=0; for(double v:x) sum+=(v-m)*(v-m);
        return Math.sqrt(sum/(x.length-1));
    }
    static double statistic(double[] x,double margin) { return (mean(x)-margin)/(sd(x)/Math.sqrt(x.length)); }
    static double wild(double[] x,double margin,Random rng,int draws) {
        double observed=statistic(x,margin);
        if(!Double.isFinite(observed) || sd(x)==0) return 1;
        double m=mean(x); double[] sample=new double[x.length]; int extreme=0;
        for(int b=0;b<draws;b++) {
            // Work in null-centered coordinates: translation by the margin cancels in t*.
            for(int i=0;i<x.length;i++) sample[i]=(rng.nextBoolean()?1:-1)*(x[i]-m);
            double t=statistic(sample,0);
            if(!Double.isFinite(t) || sd(sample)==0 || t<=observed) extreme++;
        }
        return (1.0+extreme)/(draws+1.0);
    }
    record Bca(double[] sorted,double bias,double acceleration,String reason) {
        boolean valid() { return reason.isEmpty(); }
        Double at(double alpha) {
            if(!valid()) return null;
            double z=NORMAL.inverseCumulativeProbability(alpha),d=1-acceleration*(bias+z);
            if(!Double.isFinite(d) || d<=0) return null;
            double q=NORMAL.cumulativeProbability(bias+(bias+z)/d);
            return Double.isFinite(q) && q>=0 && q<=1?quantile(sorted,q):null;
        }
    }
    static Bca bca(double[] x,Random rng,int draws) {
        if(x.length<2 || !Double.isFinite(sd(x)) || sd(x)==0) return new Bca(null,0,0,"ZERO_VARIANCE");
        double observed=mean(x); double[] bootstrap=new double[draws]; int below=0;
        for(int b=0;b<draws;b++) {
            double sum=0; for(int i=0;i<x.length;i++) sum+=x[rng.nextInt(x.length)];
            bootstrap[b]=sum/x.length; if(bootstrap[b]<observed) below++;
        }
        if(below==0 || below==draws) return new Bca(null,0,0,"BCA_BIAS_EXTREME");
        double[] jack=new double[x.length];
        for(int i=0;i<x.length;i++) { double sum=0; for(int j=0;j<x.length;j++) if(j!=i) sum+=x[j]; jack[i]=sum/(x.length-1); }
        double jm=mean(jack),s2=0,s3=0;
        for(double v:jack) { double d=jm-v; s2+=d*d; s3+=d*d*d; }
        double denominator=6*Math.pow(s2,1.5);
        if(denominator==0 || !Double.isFinite(denominator)) return new Bca(null,0,0,"BCA_JACKKNIFE_UNDEFINED");
        Arrays.sort(bootstrap);
        return new Bca(bootstrap,NORMAL.inverseCumulativeProbability((double)below/draws),s3/denominator,"");
    }
    static double quantile(double[] sorted,double q) {
        if(sorted.length==0 || !Double.isFinite(q) || q<0 || q>1) throw new IllegalArgumentException("Invalid type-7 quantile");
        double h=(sorted.length-1)*q; int lo=(int)Math.floor(h),hi=(int)Math.ceil(h);
        return sorted[lo]+(h-lo)*(sorted[hi]-sorted[lo]);
    }
    static double[] holm(double[] p) {
        if(p.length!=18) throw new IllegalArgumentException("Required fixed family of 18");
        var order=new ArrayList<Integer>();
        for(int i=0;i<p.length;i++) { if(!Double.isFinite(p[i]) || p[i]<0 || p[i]>1) throw new IllegalArgumentException("Invalid p"); order.add(i); }
        order.sort(Comparator.comparingDouble((Integer i)->p[i]).thenComparingInt(i->i));
        double[] adjusted=new double[18]; double previous=0;
        for(int rank=0;rank<18;rank++) { int i=order.get(rank); previous=Math.min(1,Math.max(previous,(18-rank)*p[i])); adjusted[i]=previous; }
        return adjusted;
    }
    static boolean claim(double p,double practicalUpper,double niUpper,boolean energy) {
        return p<=.05 && practicalUpper<=(energy?Math.log(.95):-.01) && niUpper<(energy?.01:Math.log(1.05));
    }
}
