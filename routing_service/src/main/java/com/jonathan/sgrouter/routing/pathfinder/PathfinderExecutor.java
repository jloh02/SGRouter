package com.jonathan.sgrouter.routing.pathfinder;

import com.jonathan.sgrouter.routing.RoutingApplication;
import com.jonathan.sgrouter.routing.models.Node;
import com.jonathan.sgrouter.routing.models.NodeDist;
import com.jonathan.sgrouter.routing.models.NodeDistList;
import com.jonathan.sgrouter.routing.models.RouteList;
import com.jonathan.sgrouter.routing.models.SubRoute;
import com.jonathan.sgrouter.routing.utils.CloudStorageHandler;
import com.jonathan.sgrouter.routing.utils.DatastoreHandler;
import com.jonathan.sgrouter.routing.utils.SQLiteHandler;
import com.jonathan.sgrouter.routing.utils.Utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.opengis.referencing.FactoryException;

@Slf4j
public class PathfinderExecutor {
  public static final int kShortest = 3;
  public static HashMap<String, ArrayList<SubRoute>> dp;
  public static SQLiteHandler sqh;
  public static RouteList routes;

  public static volatile boolean threadInterrupt;

  public static RouteList route(double startLat, double startLon, double endLat, double endLon) {
    threadInterrupt = false;

    sqh = new SQLiteHandler();
    if (RoutingApplication.appengineDeployment) CloudStorageHandler.downloadDB();
    else {
      try (InputStream is = new FileInputStream("archive/12_sun_graph.db");OutputStream os = new FileOutputStream("graph.db")){
        byte[] buffer = new byte[64000000];
        int length;
        while ((length = is.read(buffer)) > 0) 
          os.write(buffer, 0, length);
      } catch(Exception e){
        System.exit(1);
      }
    }

    routes = new RouteList(kShortest);
    dp = new HashMap<>();

    double walkSpeed = DatastoreHandler.getWalkSpeed();
    if (walkSpeed < 0) return routes;

    ArrayList<Node> nodes = sqh.getNodes();
    NodeDistList starts = new NodeDistList(5), ends = new NodeDistList(5);
    try {
      GeodeticCalculator srcGC = new GeodeticCalculator(CRS.parseWKT(Utils.getLatLonWKT()));
      srcGC.setStartingGeographicPoint(startLon, startLat);
      GeodeticCalculator desGC = new GeodeticCalculator(CRS.parseWKT(Utils.getLatLonWKT()));
      desGC.setStartingGeographicPoint(endLon, endLat);
      for (Node n : nodes) {
        if (!Utils.isBusStop(n.getSrcKey()) && !n.getSrcKey().contains("EXIT")) continue;
        srcGC.setDestinationGeographicPoint(n.getLon(), n.getLat());
        desGC.setDestinationGeographicPoint(n.getLon(), n.getLat());

        starts.add(new NodeDist(n.getSrcKey(), srcGC.getOrthodromicDistance()));
        ends.add(new NodeDist(n.getSrcKey(), desGC.getOrthodromicDistance()));
      }
    } catch (FactoryException e) {
      log.error(e.getMessage());
    }

    log.debug("Start Nodes: {}", starts.toString());
    log.debug("End Nodes: {}", ends.toString());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    for (NodeDist s : starts) {
      for (NodeDist e : ends) {
        executor.execute(
            new Thread(
                new Pathfinder(
                    nodes,
                    s.getSrc(),
                    e.getSrc(),
                    s.getDist() * 0.001 / walkSpeed,
                    e.getDist() * 0.001 / walkSpeed)));
      }
    }

    // Wait for all threads to finish execution or 30s timeout
    executor.shutdown();
    synchronized (executor) {
      try {
        if (!executor.awaitTermination(10000, TimeUnit.MILLISECONDS))
          log.debug("Routing timed out");
      } catch (InterruptedException e) {
        log.error(e.getMessage());
      }
    }
    threadInterrupt = true;
    executor.shutdownNow();

    log.debug("Number of routes found: {}", routes.size());
    log.debug("Routes:\n{}", routes.toString());

    sqh.close();

    return routes;
  }
}
