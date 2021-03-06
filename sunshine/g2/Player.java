package sunshine.g2;
import java.util.ArrayList;

import java.util.List;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import java.util.*;

import sunshine.sim.Command;
import sunshine.sim.Tractor;
import sunshine.sim.CommandType;
import sunshine.sim.Point;



public class Player implements sunshine.sim.Player {
    // Random seed of 42.
    private int seed = 42;
    private Random rand;
    private Point dest;
    List<Point> bales;
    List<Point> balesList;
    Point balesListCenter;
    List<Point> clusterAnchors;
    private Map<Integer, List<Command>> commandCenter;
    private Map<Point, List<Point>> farPoints;
    private List<Cluster> sortedClusters;
    private int numTractors;
    private double edgeLength;
    private double time;
    private int numBins;
    private List<Double> processOrder;
    private Map<Double, Cluster> time2Cluster;

    public Player() {
        rand = new Random(seed);
        commandCenter = new HashMap<Integer, List<Command>>();
        farPoints = new HashMap<Point, List<Point>>();
        sortedClusters = new ArrayList<Cluster>();
        numTractors = 0;
        edgeLength = 0.0;
        time = 0.0;
        numBins = 0;
        processOrder = new ArrayList<Double>();
        time2Cluster = new HashMap<Double, Cluster>();
    }
    
    public void init(List<Point> bales, int n, double m, double t)
    {
        this.bales = new ArrayList<Point>(bales);
        clusterAnchors = new ArrayList<Point>();
        Collections.sort(this.bales, 
            new Comparator(){
                @Override
                public int compare(Object o1, Object o2) {
                    Point p1 = (Point) o1;
                    Point p2 = (Point) o2;
                    return - (int)Math.signum(p1.x*p1.x + p1.y*p1.y - p2.x*p2.x - p2.y*p2.y);
               }
            } 
        );

        numTractors = n;
        edgeLength = m;
        time = t;
        numBins = numTractors / 2;
        for (int i = 0; i < numTractors; i++) {
            List<Command> commands = new ArrayList<Command>();
            commandCenter.put(i, commands);
        }
        List<Double> processTime = new ArrayList<Double>();
        while (this.bales.size() != 0) {
            Point p = this.bales.remove(0);
            double disToOrigin = calcEucDistance(new Point(0.0, 0.0), p);
            if (disToOrigin > 260) {
                List<Point> ten = getNearestTenBales(p);
                // farPoints.put(p, ten);
                List<Point> c = new ArrayList<Point>(ten);
                c.add(p);
                Cluster cluster_new = computeCentroid(c);
                sortedClusters.add(cluster_new);
                
                ////////////////////////////////////////to do!!!!!!!!!!!!!!!! fill the queue
                double time = calculateTime(cluster_new, false);
                processTime.add(time);
                time2Cluster.put(time, cluster_new);
            }
            else {
                this.bales.add(0, p);
                break;
            }
        }
        //KMeans(greaterThan,10000);
        // List<Cluster> temp = new ArrayList<Cluster>();
        // for (Cluster cluster: sortedClusters) {
        //     cluster = computeCentroid(cluster);
        //     temp.add(cluster);
        // }
        // sortedClusters = temp;
        Collections.sort(processTime);
        while (processTime.size() != 0) {
            List<Integer> batch = getOneTripOrder(processTime);
            for (int i = batch.get(1) - 1; i >= 0; i--) {
                double time = processTime.remove(i);
                processOrder.add(time);
            }
        }
        Collections.sort(sortedClusters);
    }


    private double calculateTime(Cluster cluster, boolean firstTime) {
        double time = 660.0;
        double haulDis = calcEucDistance(new Point(0.0, 0.0), cluster.getAnchor());
        time += haulDis * 2 / 4;
        List<Point> bales = cluster.getAll();
        for (Point bale: bales) {
            double sprintDis = calcEucDistance(cluster.getAnchor(), bale);
            time += sprintDis * 2 / 10;
        }
        
        if (firstTime) {
            time -= 60;
        }

        return time;
    }

    private List<Integer> getOneTripOrder(List<Double> processTime) {
        List<Integer> range = new ArrayList<Integer>();
        if (processTime.size() < numTractors) {
            range.add(0);
            range.add(processTime.size());
            return range;
        }

        List<Double> offsets = new ArrayList<Double>();
        for (int i = 1; i < processTime.size(); i++) {
            offsets.add(processTime.get(i) - processTime.get(i - 1));
        }

        List<Double> offsetTractors = new ArrayList<Double>();
        for (int i = 0; i < offsets.size() - numTractors + 1; i++) {
            double offset = 0.0;
            for (int j = 0; j < numTractors - 1; j++) {
                offset += offsets.get(i + j);
            }
            offsetTractors.add(offset);
        }

        double min = Double.MAX_VALUE;
        int index = 0;
        for (int i = 0; i < offsetTractors.size(); i++) {
            if (offsetTractors.get(i) < min) {
                index = i;
            }
        }
        range.add(index);
        range.add(numTractors);
        return range;
    }

    public Cluster computeCentroid(List<Point> bales) {
        double xSum = 0, ySum = 0;
        
        for (int i=0; i < bales.size(); i++) {
            Point bale = bales.get(i);
            xSum += bale.x;
            ySum += bale.y;
        }
        
        Point centroid = new Point(xSum/bales.size(), ySum/bales.size());
        
        double minDistance = 999999999;
        int minIndex = 0;
        Point bale;
        double distance;
        for (int i = 0; i < bales.size(); i++) {
            bale = bales.get(i);
            distance = calcEucDistance(centroid, bale);
            if (distance < minDistance) {
                minDistance = distance;
                minIndex = i;
            }
        }

        Point centerBale = bales.remove(minIndex);
        Cluster cluster = new Cluster(centerBale,bales);
        return cluster;
    }
    // when the tractor is back to the original
    public Point lazyMove(Point current, Point dest, double diff) 
    {
        double distance = calcEucDistance(current, dest);
        if (distance < 1) {
            return current;
        }

        double xDif = dest.x - current.x;
        double yDif = dest.y - current.y;

        double newX = dest.x + (xDif * diff) / distance;
        double newY = dest.y + (yDif * diff) / distance;
        Point lazyPoint = new Point(newX, newY);
        return lazyPoint;
    }

    // private void oneTrip(Tractor tractor) {
    //     if (sortedClusters.size() != 0) {
    //         Cluster cluster = sortedClusters.remove(sortedClusters.size() - 1);
    //         Point parking = calculateParking(cluster.getAnchor());
    //         collectWithTrailerDistant(tractor, parking, cluster.getAll());
    //     }
    //     else {
    //         Point p = bales.remove(0);
    //         collectWithoutTrailer(tractor, p);
    //     }
    // }

    private void oneTrip(Tractor tractor) {
        if (processOrder.size() != 0) {
            Cluster cluster = time2Cluster.get(processOrder.remove(0));
            Point parking = calculateParking(cluster.getAnchor());
            collectWithTrailerDistant(tractor, parking, cluster.getAll());
        }
        else {
            Point p = bales.remove(0);
            collectWithoutTrailer(tractor, p);
        }
    }

    private Point calculateParking(Point p) {
        int dis = (int) Math.sqrt(p.x * p.x + p.y * p.y);
        int x = (dis) * (int) p.x / dis;
        int y = (dis) * (int) p.y / dis;
        return new Point(x, y);
    }

    private void collectWithoutTrailer(Tractor tractor, Point p) {
        int tractorID = tractor.getId();
        
        List<Command> commands = commandCenter.get(tractorID);
        if (tractor.getAttachedTrailer() != null) {
            commands.add(new Command(CommandType.DETATCH));
        }

        Point tractorPos = tractor.getLocation();
        Point newP = lazyMove(tractorPos, p, -0.20); // ********************************* Lazy Move ***************

        commands.add(Command.createMoveCommand(newP));
        commands.add(new Command(CommandType.LOAD));


        Point home = new Point(0.0, 0.0);
        tractorPos = tractor.getLocation();
        newP = lazyMove(newP, home, -0.99); // ********************************* Lazy Move ***************

        commands.add(Command.createMoveCommand(newP));
        commands.add(new Command(CommandType.UNLOAD));
    }

    private void collectWithTrailer(Tractor tractor, Point p, List<Point> ten) {
        int tractorID = tractor.getId();
        List<Command> commands = commandCenter.get(tractorID);

        // forward trip
        if (tractor.getAttachedTrailer() == null) {
            commands.add(new Command(CommandType.ATTACH));
        }

        Point tractorPos = tractor.getLocation();
        Point newP = lazyMove(tractorPos, p, 0.99);

        commands.add(Command.createMoveCommand(newP));
        commands.add(new Command(CommandType.DETATCH));
        for (Point bale : ten) {
            commands.add(Command.createMoveCommand(bale));
            commands.add(new Command(CommandType.LOAD));
            commands.add(Command.createMoveCommand(p));
            commands.add(new Command(CommandType.STACK));
        }
        commands.add(new Command(CommandType.LOAD));
        commands.add(new Command(CommandType.ATTACH));

        //backward trip
        Point home = new Point(0.0, 0.0);
        tractorPos = tractor.getLocation();
        newP = lazyMove(tractorPos, home, 0.99);

        commands.add(Command.createMoveCommand(newP));
        commands.add(new Command(CommandType.DETATCH));
        commands.add(new Command(CommandType.UNLOAD));
        for (int i = 0; i < ten.size(); i++) {
            commands.add(new Command(CommandType.UNSTACK));
            commands.add(new Command(CommandType.UNLOAD));
        }
    }


    private void collectWithTrailerDistant(Tractor tractor, Point parking, List<Point> eleven) {
        int tractorID = tractor.getId();
        List<Command> commands = commandCenter.get(tractorID);

        // forward trip
        if (tractor.getAttachedTrailer() == null) {
            commands.add(new Command(CommandType.ATTACH));
        }

        Point tractorPos = tractor.getLocation();
        Point newP = lazyMove(tractorPos, parking, -0.99); // ********************************* Lazy Move ***************
        parking = newP;

        commands.add(Command.createMoveCommand(parking));
        commands.add(new Command(CommandType.DETATCH));
        for (int i = 0; i < eleven.size() - 1; i++) {
            Point bale = eleven.get(i);

            newP = lazyMove(parking, bale, -0.75);

            commands.add(Command.createMoveCommand(bale));
            commands.add(new Command(CommandType.LOAD));

            newP = lazyMove(bale, parking, -0.99); // ********************************* Lazy Move ***************

            commands.add(Command.createMoveCommand(newP));
            commands.add(new Command(CommandType.STACK));
        }
        commands.add(Command.createMoveCommand(eleven.get(eleven.size() - 1)));
        commands.add(new Command(CommandType.LOAD));

        tractorPos = tractor.getLocation();
        newP = lazyMove(new Point(0, 0), parking, -0.99);

        commands.add(Command.createMoveCommand(newP));
        commands.add(new Command(CommandType.ATTACH));

        //backward trip
        Point home = new Point(0.0, 0.0);
        tractorPos = tractor.getLocation();
        newP = lazyMove(parking, home, -0.99);

        commands.add(Command.createMoveCommand(newP));
        commands.add(new Command(CommandType.DETATCH));
        commands.add(new Command(CommandType.UNLOAD));
        for (int i = 0; i < eleven.size() - 1; i++) {
            commands.add(new Command(CommandType.UNSTACK));
            commands.add(new Command(CommandType.UNLOAD));
        }
    }

    public double calcEucDistance(Point origin, Point dest1)
    {
        Point result = new Point(0.0, 0.0);
        result.y = Math.abs(origin.y - dest1.y);
        result.x = Math.abs(origin.x - dest1.x);
        double distance = Math.sqrt((result.y)*(result.y) +(result.x)*(result.x));

        return distance;
    }

    public List<Point> getNearestTenBales(Point p)
    {
        PriorityQueue<Point> sortedToPoint = new PriorityQueue<Point>(bales.size(), 
            new Comparator(){
                public int compare(Object o1, Object o2) {
                    Point p1 = (Point) o1;
                    Point p2 = (Point) o2;
                    return (int) Math.signum((p1.x - p.x) * (p1.x - p.x) + (p1.y - p.y) * (p1.y - p.y) - (p2.x - p.x) * (p2.x - p.x) - (p2.y - p.y) * (p2.y - p.y));
               }
            }
        );

        for (Point bale : bales) {
            sortedToPoint.add(bale);
        }

        List<Point> result = new ArrayList<Point>();
        int numBales = 10;
        if (sortedToPoint.size() < numBales) {
            numBales = sortedToPoint.size();
        }
        for (int i = 0; i < numBales; i++) {
            Point point = sortedToPoint.poll();
            bales.remove(point);
            result.add(point);
        }

        return result;
    }

    public int getNearestBale()
    {
        Point target = balesListCenter;
        double minDist = 9999999999.99;
        int minIndex = 0;

        for (int i=0; i<bales.size(); i++) {
            Point bale = bales.get(i);

            Double distance = calcEucDistance(target, bale);
            if (distance < minDist)
            {
                minDist = distance;
                minIndex = i;
            }
        }

        return minIndex;
    }

    public int getNearestToOrigin(List<Point> bales)
    {
        Point target = new Point(0.0, 0.0);
        double minDist = 9999999999.99;
        int minIndex = 0;

        for (int i=0; i<bales.size(); i++) {
            Point bale = bales.get(i);

            Double distance = calcEucDistance(target, bale);
            if (distance < minDist)
            {
                minDist = distance;
                minIndex = i;
            }
        }

        return minIndex;

    }

    public int getFurthestBale()
    {
        Point home = new Point(0.0, 0.0);
        double maxDist = 0.0;
        int maxIndex = 0;

        for (int i=0; i<bales.size(); i++) {
           Point bale = bales.get(i);

           Double distance = calcEucDistance(home, bale);
           if (distance > maxDist)
           {
                maxDist = distance;
                maxIndex = i;
           }
        }

        return maxIndex;
    }
    private void unstack(Tractor tractor) {
        int tractorID = tractor.getId();
        List<Command> commands = commandCenter.get(tractorID);  
        commands.add(new Command(CommandType.UNSTACK));
        commands.add(new Command(CommandType.UNLOAD));
    }

    public Command getCommand(Tractor tractor)
    {   
        int tractorID = tractor.getId();
        List<Command> commands = commandCenter.get(tractorID);

        if (commands.size() == 0 && bales.size() != 0) {
            oneTrip(tractor);
        }

        if (commands.size() == 0) {
            unstack(tractor);
        }
        
        return commands.remove(0);
    }
    private void buildList() {
        for (int i = 0; i < 11; i++) {
            int index = getNearestBale();
            balesList.add(bales.get(index));
            bales.remove(index);
        }
    }
    private class Cluster implements Comparable<Cluster>{
        Point anchor;
        List<Point> others;

        public Cluster (Point a, List<Point> o) {
            anchor = new Point(a.x, a.y);
            others = new ArrayList<Point>(o);
        }

        public void setAnchor(Point anchor) {
            anchor = new Point(anchor.x, anchor.y);
        }

        public Point getAnchor() {
            return new Point(anchor.x, anchor.y);
        }

        public List<Point> getOthers() {
            return new ArrayList<Point>(others);
        }

        public List<Point> getAll() {
            List<Point> result = new ArrayList<Point>(others);
            result.add(new Point(anchor.x, anchor.y));
            Collections.sort(result, new Comparator(){
                @Override
                public int compare(Object o1, Object o2) {
                    Point p1 = (Point) o1;
                    Point p2 = (Point) o2;
                    return - (int)Math.signum(p1.x*p1.x + p1.y*p1.y - p2.x*p2.x - p2.y*p2.y);
               }
            } );
            return result;
        }

        @Override
        public int compareTo(Cluster cluster) {
            return (int) Math.signum(anchor.x * anchor.x + anchor.y * anchor.y - cluster.anchor.x * cluster.anchor.x - cluster.anchor.y * cluster.anchor.y);
        }
    }
    public void KMeans(List<Point> bales, int max_iter){
        double[][] points = new double[bales.size()][2];
        for(int i=0;i<bales.size();i++){
            points[i][0] = bales.get(i).x;
            points[i][1] = bales.get(i).y;
        }
        sortPointsByX(points);
        int maxIterations = max_iter;
        int clusters = bales.size()/11;
        double[][] means = new double[clusters][2];
        for(int i=0; i<means.length; i++) {
            means[i][0] = points[(int) (Math.floor((bales.size()*1.0/clusters)/2) + i*bales.size()/clusters)][0];
            means[i][1] = points[(int) (Math.floor((bales.size()*1.0/clusters)/2) + i*bales.size()/clusters)][1];
        }
        ArrayList<Integer>[] oldClusters = new ArrayList[clusters];
        ArrayList<Integer>[] newClusters = new ArrayList[clusters];
        for(int i=0; i<clusters; i++) {
            oldClusters[i] = new ArrayList<Integer>();
            newClusters[i] = new ArrayList<Integer>();
        }
        formClusters(oldClusters, means, points);
        int iterations = 0;
        while(true) {
            updateMeans(oldClusters, means, points);
            formClusters(newClusters, means, points);
            iterations++;
            if(iterations > maxIterations || checkEquality(oldClusters, newClusters))
                break;
            else
                resetClusters(oldClusters, newClusters);
        }
        storeOutputs(oldClusters, points, bales, means);
        System.out.println(sortedClusters.get(0).getAnchor().x);
        System.out.println(sortedClusters.get(0).getAnchor().y);
        System.out.println(sortedClusters.get(0).getOthers().get(0).x);
        System.out.println(sortedClusters.get(0).getOthers().get(0).y);
        System.out.println(sortedClusters.get(0).getOthers().get(1).x);
        System.out.println(sortedClusters.get(0).getOthers().get(1).y);

    }
    public void sortPointsByX(double[][] points) {
        double[] temp;
        for(int i=0; i<points.length; i++)
            for(int j=1; j<(points.length-i); j++)
            if(points[j-1][0] > points[j][0]) {
                temp = points[j-1];
                points[j-1] = points[j];
                points[j] = temp;
            }
    }
    public void updateMeans(ArrayList<Integer>[] clusterList, double[][] means, double[][] points) {
        double totalX = 0;
        double totalY = 0;
        for(int i=0; i<clusterList.length; i++) {
            totalX = 0;
            totalY = 0;
            for(int index: clusterList[i]) {
                totalX += points[index][0];
                totalY += points[index][1];
            }
            means[i][0] = totalX/clusterList[i].size();
            means[i][1] = totalY/clusterList[i].size();
        }
    }
    public void formClusters(ArrayList<Integer>[] clusterList, double[][] means, double[][] points) {
        double distance[] = new double[means.length];
        double minDistance = 999999999;
        int minIndex = 0;

        for(int i=0; i<points.length; i++) {
            minDistance = 999999999;
            for(int j=0; j<means.length; j++) {
                distance[j] = Math.sqrt(Math.pow((points[i][0] - means[j][0]), 2) + Math.pow((points[i][1] - means[j][1]), 2));
                if((distance[j] < minDistance)&&(clusterList[j].size()<=11)) {
                    minDistance = distance[j];
                    minIndex = j;
                }
            }
            clusterList[minIndex].add(i);
        }
    }
    public boolean checkEquality(ArrayList<Integer>[] oldClusters, ArrayList<Integer>[] newClusters) {
        for(int i=0; i<oldClusters.length; i++) {
            // Check only lengths first
            if(oldClusters[i].size() != newClusters[i].size())
                return false;

            // Check individual values if lengths are equal
            for(int j=0; j<oldClusters[i].size(); j++)
                if(oldClusters[i].get(j) != newClusters[i].get(j))
                    return false;
        }

        return true;
    }

    public void resetClusters(ArrayList<Integer>[] oldClusters, ArrayList<Integer>[] newClusters) {
        for(int i=0; i<newClusters.length; i++) {
            // Copy newClusters to oldClusters
            oldClusters[i].clear();
            for(int index: newClusters[i])
                oldClusters[i].add(index);

            // Clear newClusters
            newClusters[i].clear();
        }
    }

    public void storeOutputs(ArrayList<Integer>[] clusterList, double[][] points, List<Point> bales, double[][] means) {
        for(int i=0; i<clusterList.length; i++) {
            List<Point> others = new ArrayList();
            for(int index: clusterList[i]){
                Point p = bales.get(index);
                others.add(p);
            }
            Point anchor = new Point(means[i][0],means[i][1]);
            Cluster c = new Cluster(anchor,others);
            sortedClusters.add(c);

        }
    }
}