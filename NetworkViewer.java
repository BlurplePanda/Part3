/**
 * The Top level program that
 * - creates the interface
 * - loads the data from files
 * - responds to the commands from the interface
 * - displays the network and the path
 */ 


import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.*;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.*;


public class NetworkViewer {


    // Fields with GUI components that need to be accessed

    private Canvas mapCanvas;
    private TextArea displayText;
    private TextField walkingDistanceTextField;
    private Slider walkingDistanceSlider;

    private static final int LIMIT_WALKING_DISTANCE = 500;

    /**
     * Create the GUI, by constructing the Scene with its hierarchy of components.
     */
    
    public Scene setupGUI(){
            // Create the components of the GUI

            // The top level components:
            GridPane controlsGrid = new GridPane();  // grid layout of the controls
            mapCanvas = new Canvas(800, 500);
            displayText = new TextArea();

            // The controls
            Button reloadButton = new Button("Reload Map");
            Button quitButton = new Button("Quit");
            Button articulationPointsButton = new Button("Compute articulation points");

            Label walkingLabel = new Label("Set max walking dist:");
            walkingDistanceTextField = new TextField();
            walkingDistanceSlider = new Slider(0,LIMIT_WALKING_DISTANCE,0);


            // Add the control elements to the controls Grid, giving column and row)
            controlsGrid.setAlignment(Pos.CENTER);
            controlsGrid.setHgap(10);
            controlsGrid.setVgap(10);
            
            controlsGrid.add(reloadButton,             0, 0);
            controlsGrid.add(quitButton,               0, 1);
            controlsGrid.add(walkingLabel,             1, 0);
            controlsGrid.add(walkingDistanceSlider,    2, 0);
            controlsGrid.add(walkingDistanceTextField, 3, 0);
            controlsGrid.add(articulationPointsButton, 1, 1);

            //Set the handlers for the controls.
            reloadButton.setOnAction(this::handleReload);
            quitButton.setOnAction(this::handleQuit);
            articulationPointsButton.setOnAction(this::handleArticulationPoints);

            walkingDistanceTextField.setOnAction(this::handleWalkingDistance);
            walkingDistanceSlider.setOnMouseReleased(this::handleWalkingDistanceSlider);

            mapCanvas.setOnMouseClicked(this::handleMouseClick);
            mapCanvas.setOnMouseDragged(this::handleMouseDrag);
            mapCanvas.setOnMousePressed(this::handleMousePressed);
            mapCanvas.setOnScroll(this::handleMouseScroll);

            return new Scene(new VBox(controlsGrid, mapCanvas, displayText), 800, 700);
        }

    //--------------------------------
    // Fields for displaying the map
    //--------------------------------
    

    // Fields for mapping the location of nodes (Stops) to points on screen
    // Note: scrolling and zooming will change the mapOrigin and the scale.

    private static final double SCALE = 5000.0; // 5000 gives 1 pixel ~ 20 meters
    private static final double MAP_LON = 174.80; // Lon for Wellington
    private static final double MAP_LAT = -41.26; // Lat for Wellington

    private double scale = SCALE;
    private GisPoint mapOrigin = new GisPoint(MAP_LON, MAP_LAT); // Lon Lat for Wellington
    private static final double ratioLatLon = 0.73; // in Wellington ratio of latitude to longitude


    private static final int STOP_SIZE = 5; // drawing size of stops
    private static final double EDGE_WIDTH = 0.5; // drawing size of edges
    private Stop closestStop;
    private Collection<Stop> aPoints;


    // Methods to access the fields  (used in Projection class)

    public double getScale() {return scale;}
    public GisPoint getOrigin() {return mapOrigin;}
    public Canvas getMapCanvas() {return mapCanvas;}
    public double getRatioLatLon() {return ratioLatLon;}






    //---------------------------------------------
    // Initialising: 
    //---------------------------------------------

    
    // Fields for the data
    public Graph graph;         // The graph object with all the Stops, Lines, and Edges

    public Zoning zoneData;     // Data for drawing the coastline and zone boundaries


    /**
     * Entry Method: (Called by Main once the GUI has been set up)
     * Loads data into the graph from the 'data-full' directory.
     * Then draws the map.
     */ 
    public void initialise() {
        Path dataDir = Path.of(System.getProperty("user.dir"), "data-full");
        System.out.println("Loading graph from "+dataDir);
        if (loadData(dataDir)){
            reportLoad();
        } else {
            System.out.println("Loading failed; creating empty graph");
            graph = new Graph(new HashSet<Stop>(), new HashSet<Line>());
        }
        drawMap(graph);
    }

    // ---------------------------------
    //  Handling the UI: RELOAD and QUIT buttons
    // ---------------------------------

    /**
     * Handle the articulation point computing button
     */
    public void handleArticulationPoints(ActionEvent event) {
        event.consume();
        aPoints = ArticulationPoints.findArticulationPoints(graph);
        drawMap(graph);
        if (aPoints != null) {
            // highlight articulation points (red)
            for (Stop aPoint : aPoints) {
                drawStop(aPoint, STOP_SIZE*2, Color.RED);
            }

            // report articulation points in text area
            displayText.setText("Articulation points:\n");
            for (Stop stop : aPoints) {
                displayText.appendText(stop.toString()+"\n");
            }
        }
    }

    /**
     * Handle the ReloadButton by selecting the directory which should have the data files in it.
     */
    public void handleReload(ActionEvent event){
        event.consume();
        DirectoryChooser dataDirectoryChooser = new DirectoryChooser();
        dataDirectoryChooser.setTitle("Select Folder with data files to load");
        dataDirectoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        Path dataDirectory = dataDirectoryChooser.showDialog(Main.stage).toPath();

        System.out.println("Loading graph from "+dataDirectory);
        if (!loadData(dataDirectory)){
            System.out.println("Loading failed; graph not changed");
            return;
        }
        reportLoad();

        scale = SCALE;
        mapOrigin = new GisPoint(MAP_LON, MAP_LAT);

        walkingDistanceSlider.setValue(0);
        walkingDistanceTextField.setText("0");

        resetSearch();

        drawMap(graph);
    }


    /**
     * Handle the QuitButton 
     */
    public void handleQuit(ActionEvent event) {
        event.consume();
        System.exit(0); // system exit with status 0 - normal
    }


    // ------------------------------------------
    // Handling the UI: WALKING
    // Setting the walking connections between stops
    // The maximum distance of a walking connection can be set by entering
    // the distance into the text field or using the slider.
    // In either case, the program must remove all existing walking edges
    // and create the new edges that are within the new maximum distance.
    // --------------------------------------------

    /**
     *  Handles entering distance in the text field
     *  Sets the walking slider.
     *  recomputes the walking edges
     */
    public void handleWalkingDistance(ActionEvent event) {
        double dist = walkingDistanceSlider.getValue();        
        String distStr = walkingDistanceTextField.getText();
        try {dist = Math.max(Math.min(Math.round(Double.parseDouble(distStr)), LIMIT_WALKING_DISTANCE),0);}
        catch (Exception e){}
        //System.out.println("Setting walking distance (in tf) to " + dist);
        walkingDistanceTextField.setText(Integer.toString((int)dist));
        walkingDistanceSlider.setValue(dist);         
        graph.removeWalkingEdges();
        if (dist>0){
            graph.recomputeWalkingEdges(dist);
        } 
        resetSearch();
        drawMap(graph);
    }

    /**
     *  Handles entering distance on the walking slider.
     *  Sets the text field
     *  recomputes the walking edges
     */
    public void handleWalkingDistanceSlider(MouseEvent event){
        double dist = Math.round(walkingDistanceSlider.getValue());
        walkingDistanceTextField.setText(Double.toString(dist));
        // System.out.println("Setting walking distance (on slider) to " + dist);
        graph.removeWalkingEdges();
        if (dist>0){
            graph.recomputeWalkingEdges(dist);
        } 

        resetSearch(); 
        drawMap(graph);
    }


    /** Reset the search parameters and the path because the graph has changed.
     *  WILL NEED TO BE MODIFIED FOR THE COMPONENTS OR ARTICULATION PTS VERSION
     *  (since they don't use the pathEdges or start and goal locations.)
     */
    public void resetSearch(){
        closestStop = null;
        aPoints = null;
        displayText.clear();
    }


    /*
     * Handle Mouse clicks on the canvas to select stops.
     * Find the node closest to the click then
     * display the stop info in the text area
     */
    public void handleMouseClick(MouseEvent event) {
        if (dragActive) {
            dragActive = false;
            return;
        }
        // System.out.println("Mouse click event " + event.getEventType());

        // find node closed to mouse click
        Point2D screenPoint = new Point2D(event.getX(), event.getY());
        GisPoint location = Projection.screen2Model(screenPoint, this);

        closestStop = findClosestStop(location, graph);

        drawMap(graph);

        // highlight selected stop (green)
        if (closestStop != null) {
            drawStop(closestStop, STOP_SIZE*2, Color.GREEN);
            displayText.setText(closestStop.toString());
        }

        event.consume();
    }

    /**
     * Find the closest stop to the given Gis Point location
     * @param x
     * @param y
     * @param graph
     * @return
     */
    public Stop findClosestStop(GisPoint loc, Graph graph) {
        double minDist = Double.MAX_VALUE;
        Stop closestStop = null;
        // This is slow but could be faster if you use a quadtree or K-D tree
        for (Stop stop : graph.getStops()) {
            double dist = stop.distanceTo(loc);
            if (dist < minDist) {
                minDist = dist;
                closestStop = stop;
            };
        }
        if (closestStop != null) {
            return closestStop;
        }
        return null;
    }



    // -----------------------------------------
    // Handling the UI: ZOOM and SCROLL
    // -----------------------------------------

    // Mouse scroll for zoom
    public void handleMouseScroll(ScrollEvent event) {
        // change the zoom level
        double changefactor = 1 + (event.getDeltaY() / 400);
        scale *= changefactor;
        // update the graph
        drawMap(graph);
        event.consume();
    }


    public double dragStartX = 0;
    public double dragStartY = 0;
    // handle starting drag on canvas
    public void handleMousePressed(MouseEvent event) {
        dragStartX = event.getX();
        dragStartY = event.getY();
        event.consume();
    }

    // used to prevent drag from creating a click
    private Boolean dragActive = false;

    // handleMouse Drag
    public void handleMouseDrag(MouseEvent event) {
        // pan the map
        double dx = event.getX() - dragStartX;
        double dy = event.getY() - dragStartY;
        dragStartX = event.getX();
        dragStartY = event.getY();
        mapOrigin.move(-dx / (scale * ratioLatLon), (dy / scale));

        drawMap(graph);
        // set drag active true to avoid clicks highlighting nodes
        dragActive = true;
        event.consume();
    }


    // -----------------------------------------
    //  LOADING DATA FILES and CONSTRUCTING THE GRAPH
    // -----------------------------------------

    /**
     * Load the stops and lines data from the given directory,
     * Load the fare zones data
     * Create the graph and display the graph
     * DO NOT MODIFY THESE METHODS FOR PARTS 1, 2 or 3 - IT IS NEEDED FOR MARKING.
     */
    public boolean loadData(Path dataDirectory){ 
        // load the input files
        if (!dataDirectory.resolve("stops.txt").toFile().exists() ||
            !dataDirectory.resolve("lines.txt").toFile().exists()){
            System.out.println("DIRECTORY DOES NOT CONTAIN REQUIRED DATA FILES");
            return false;
        }

        Map<String, Stop> stopMap = loadStops(dataDirectory.resolve("stops.txt"));

        Collection<Line> lines = loadLines(dataDirectory.resolve("lines.txt"), stopMap);

        zoneData = null;
        if (dataDirectory.resolve("WellingtonZones.csv").toFile().exists()){
            zoneData = new Zoning(dataDirectory.resolve("WellingtonZones.csv"));
        }

        // Create the graph (ie, all the edges)
        this.graph = new Graph(stopMap.values(), lines);

        return true;
    }


    /** Load the stop data from the stop file
     * file contains a line for each stop:
     *     stop_id, stop_code, stop_name, stop_desc,
     *     stop_lat, stop_lon, zone_id, location_type,
     *     parent_station, stop_url, stop_timezone
     *  Note that there is a header line in the file.
     *  Not all the info for each stop is necessary
     * Returns a Map of all the Stops:
     *   key = the stop_id
     *   value = a Stop object containing the name, id, longitude, and latitude
     */
    public static Map<String, Stop> loadStops(Path stopsFile) {
        Map<String, Stop> stops = new HashMap<String, Stop>();

        try {
            List<String> dataLines = Files.readAllLines(stopsFile);
            dataLines.remove(0);// throw away the top line of the file
            for (String dataLine : dataLines){
                // tokenise the dataLine by splitting it on tabs
                String[] tokens = dataLine.split("\t");
                if (tokens.length >= 6) {
                    // process the tokens
                    String stopId = tokens[0];
                    String stopName = tokens[2];
                    double lat = Double.valueOf(tokens[4]);
                    double lon = Double.valueOf(tokens[5]);
                    stops.put(stopId, new Stop(lon, lat, stopName, stopId));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Reading the stops file failed.");
        }
        return stops;
    }

    /** Load the line data from the lines file
     * File contains: line_id, stop_id, timepoint
     * Uses the stopMap to turn the stop_id's into Stops
     */
    public static Collection<Line> loadLines(Path lineFile, Map<String,Stop> stopMap) {
        if (stopMap.isEmpty()){
            throw new RuntimeException("loadLines given an empty stopMap.");
        }
        Map<String, Line> lineMap = new HashMap<String, Line>();
        try {
            List<String> dataLines = Files.readAllLines(lineFile);
            dataLines.remove(0); //throw away the top line of the file.
            for (String dataLine : dataLines){// read in each line of the file
                // tokenise the line by splitting it on tabs".
                String[] tokens = dataLine.split("\t");
                if (tokens.length >= 3) {
                    // process the tokens
                    String lineId = tokens[0];
                    Line line = lineMap.get(lineId);
                    if (line == null) {
                        //System.out.println("Loading line: "+lineId);
                        line = new Line(lineId);
                        lineMap.put(lineId, line);
                    }
                    int time = Integer.parseInt(tokens[2]);
                    String stopId = tokens[1];
                    Stop stop = stopMap.get(stopId);
                    if (stop==null){
                        System.out.println("Line "+lineId+" has unknown stop "+stopId+" at "+time);
                    }
                    else {
                        line.addStop(stop, time);
                        stop.addLine(line);   // record that this stop is on this line
                    }
                }
                else {
                    System.out.println("Line file has broken entry: "+dataLine);
                }
                    
            }
        } catch (IOException e) {throw new RuntimeException("Loading the lines file failed.");}
        return lineMap.values();
    }

    /**
     * Report details of a successful data load
     */
    private void reportLoad(){
        System.out.println("Loaded "+ graph.getStops().size()+" stops");
        System.out.println("Loaded "+ graph.getLines().size()+" lines");
        System.out.println("Constructed Graph with "+ graph.getEdges().size()+" edges");
        System.out.println("---------------------");
    }

    // -----------------------------------------
    //  DRAWING THE NETWORK MAP,
    //   - draw the zone boundaries
    //   - draw all the edges
    //   - highlight the path, and report details of path 
    //   - draw all the nodes (stops)
    //   - highlight the start and goal nodes
    // -----------------------------------------

    /**
     * Draw the current graph, along with the current path, if there is one.
     * If there is a path, it also updates the displayText text area with a
     * text description of the path.
     */
    public void drawMap(Graph graph) {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());

        if (graph == null) {return;}

        if (zoneData != null){drawFareZones(gc);}

        // draw all the edges
        for (Edge edge : graph.getEdges()){
            drawEdge(edge);
        }

        for (Stop stop : graph.getStops()) {
            drawStop(stop, STOP_SIZE, Color.BLUE);
        }
    }


    /**
     * Draw an edge in the graph
     * The color of the edge depends on the transportation type.
     */
    private void drawEdge(Edge edge){
        Color color = switch (edge.transpType()) {
        case Transport.BUS ->     Color.DARKRED;
        case Transport.TRAIN ->   Color.DARKORANGE;
        case Transport.WALKING -> Color.DARKVIOLET;
        default ->                Color.DARKGREEN; };
        drawEdge(edge, EDGE_WIDTH, color);
    }


    /**
     * Draw a highlighted edge  (for the path)
     * The color of the edge depends on the transportation type.
     */
    private void drawHighlightedEdge(Edge edge){
        Color color = switch (edge.transpType()) {
        case Transport.BUS ->     Color.RED;
        case Transport.TRAIN ->   Color.ORANGE;
        case Transport.WALKING -> Color.PURPLE;
        default ->                Color.GREEN; };
        drawEdge(edge, EDGE_WIDTH*6, color);
    }

    /**
     * Draw an edge with specified width and color
     */
    private void drawEdge(Edge edge, double width, Color color){
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.setLineWidth(width);
        gc.setStroke(color);
        Point2D from = Projection.model2Screen(edge.fromStop().getPoint(), this);
        Point2D to = Projection.model2Screen(edge.toStop().getPoint(), this);
        gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());
    }

    /*
     * Draw a stop with the given size and color.
     */
    public void drawStop(Stop stop, double size, Color color) {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.setFill(color);
        Point2D point = Projection.model2Screen(stop.getPoint(), this);
        gc.fillOval(point.getX() - size / 2, point.getY() - size / 2, size, size);
    }

    /**
     * Draw the fare zones (if it exists)
     * This shows the outline of Wellington.
     */
    public void drawFareZones(GraphicsContext gc) {
        if (zoneData == null){ return; }
        gc.setFill(Color.LIGHTBLUE);
        gc.setStroke(Color.LIGHTBLUE);
        gc.setLineWidth(1);
        // for loop over values in the Hashmap of shapes
        for (Shape zone : zoneData.getZones().values()) {
            for (GeoPoly poly : zone.getShapes()) {
                for (int k = 0; k < poly.getPoints().size() - 1; k++) {
                    Point2D start = Projection.model2Screen(poly.getPoints().get(k), this);
                    Point2D end = Projection.model2Screen(poly.getPoints().get(k + 1), this);
                    gc.strokeLine(start.getX(), start.getY(), end.getX(), end.getY());
                }
            }
        }
    }


}
