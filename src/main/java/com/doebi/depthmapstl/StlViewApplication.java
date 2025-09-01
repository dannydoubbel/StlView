package com.doebi.depthmapstl;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.stage.FileChooser;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import java.io.IOException;

public class StlViewApplication extends Application {
    private Label statusLabel = new Label("Ready");
    private String stlName = "";
    private int stlPointCount = 0;
    private int stlFaceCount = 0;

    @Override
    public void start(Stage stage) {
        //Left  side : 3D viewer
        // Root group for 3D content
        Group modelGroup = new Group();
        SubScene subScene = new SubScene(modelGroup, 600, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.BLACK);

        PerspectiveCamera cam = new PerspectiveCamera(true);
        cam.setTranslateZ(-1000);
        cam.setNearClip(0.1);  //
        cam.setFarClip(10000);//
        subScene.setCamera(cam);

         StackPane leftPane = new StackPane(subScene);

        // Right side: placeholder (later PNG previews)
        VBox rightPane = new VBox();
        rightPane.setStyle("-fx-background-color: #2b2b2b;");
        Label placeholder = new Label("PNG preview area (coming soon)");
        placeholder.setTextFill(Color.WHITE);
        rightPane.getChildren().add(placeholder);

        // Split pane
        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.7); // 70% left, 30% right initially



        // Menu bar
        Menu fileMenu = new Menu("File");
        MenuItem openItem = new MenuItem("Open STL...");
        fileMenu.getItems().add(openItem);
        Menu viewMenu = new Menu("View");
        MenuItem resetView = new MenuItem("Reset View");
        viewMenu.getItems().add(resetView);

        MenuBar menuBar = new MenuBar(fileMenu, viewMenu);

        HBox statusBar = new HBox(statusLabel);
        statusBar.setStyle("-fx-background-color: #333333; -fx-padding: 4;");
        statusLabel.setTextFill(Color.WHITE);

        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(split);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1200, 700);
        stage.setScene(scene);
        stage.setTitle("STL Viewer with split screen");
        stage.show();

        // Open action
        openItem.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("STL files", "*.stl"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                TriangleMesh mesh = loadStl(file);
                MeshView meshView = new MeshView(mesh);

                // inside openItem.setOnAction, after you create meshView:
                Rotate rx = new Rotate(-30, Rotate.X_AXIS);
                Rotate ry = new Rotate(30, Rotate.Y_AXIS);
                meshView.getTransforms().addAll(rx, ry);


                meshView.setMaterial(new PhongMaterial(Color.LIGHTGRAY));
                stlName = file.getName();
                stlPointCount = mesh.getPoints().size() / 3;
                stlFaceCount = mesh.getFaces().size() / 6;
                updateStatusBar(rx,ry);

                modelGroup.getChildren().setAll(meshView); // replace previous model
                addMouseControl(meshView, scene,subScene, stage, rx,ry);
                //resetBtn.fire(); // set to default isometric
                // hook reset menu
                resetView.setOnAction(e1 -> resetView(meshView, rx, ry));
            }
        });

    }

    private void updateStatusBar(Rotate rx, Rotate ry   ) {
        statusLabel.setText("Loaded: " + stlName +
                " | Vertices: " + stlPointCount +
                " | Faces: " + stlFaceCount +
                "Rotation: X=" + String.format("%.1f", rx.getAngle()) +
                "°, Y=" + String.format("%.1f", ry.getAngle()) + "°"  );
    }


    private TriangleMesh loadStl(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[80];
            if (fis.read(header) != 80) throw new IOException("Invalid STL header");

            byte[] countBytes = new byte[4];
            int read = fis.read(countBytes);
            if (read != 4) {
                System.out.println("Could not read triangle count");
                throw new IOException("Could not read triangle count");
            }
            int triCount = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

            // Heuristic: if file is large enough for binary and header doesn't start with "solid"
            String headerStr = new String(header).trim().toLowerCase(Locale.ROOT);
            long expectedSize = 80 + 4 + (50L * triCount);
            if (file.length() == expectedSize && !headerStr.startsWith("solid")) {
                return loadBinaryStl(file, triCount);
            } else {
                return loadAsciiStl(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            return new TriangleMesh();
        }
    }








    // Very simple ASCII STL parser
    private TriangleMesh loadAsciiStl(File file) throws IOException {
        TriangleMesh mesh = new TriangleMesh();
        List<Float> points = new ArrayList<>();
        List<Integer> faces = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            List<Integer> triIndices = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.ROOT);
                if (line.startsWith("vertex")) {
                    String[] parts = line.split("\\s+");
                    float x = Float.parseFloat(parts[1]);
                    float y = Float.parseFloat(parts[2]);
                    float z = Float.parseFloat(parts[3]);
                    int index = points.size() / 3;
                    points.add(x); points.add(y); points.add(z);
                    triIndices.add(index);
                }
                if (line.startsWith("endfacet") && triIndices.size() == 3) {
                    faces.add(triIndices.get(0)); faces.add(0);
                    faces.add(triIndices.get(1)); faces.add(0);
                    faces.add(triIndices.get(2)); faces.add(0);
                    triIndices.clear();
                }
            }
        }

        mesh.getPoints().setAll(toFloatArray(points));
        mesh.getTexCoords().setAll(0,0);
        mesh.getFaces().setAll(faces.stream().mapToInt(i->i).toArray());
        return mesh;
    }

    private TriangleMesh loadBinaryStl(File file, int triCount) throws IOException {
        TriangleMesh mesh = new TriangleMesh();
        List<Float> points = new ArrayList<>();
        List<Integer> faces = new ArrayList<>();
        Map<String,Integer> pointIndexMap = new HashMap<>();

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            dis.skipBytes(84); // header + tri count already read in heuristic
            for (int i = 0; i < triCount; i++) {
                dis.readFloat(); dis.readFloat(); dis.readFloat(); // normal (ignored)
                int[] triIdx = new int[3];
                for (int v = 0; v < 3; v++) {
                    float x = dis.readFloat();
                    float y = dis.readFloat();
                    float z = dis.readFloat();
                    String key = x + "," + y + "," + z;
                    triIdx[v] = pointIndexMap.computeIfAbsent(key, k -> {
                        int idx = points.size() / 3;
                        points.add(x); points.add(y); points.add(z);
                        return idx;
                    });
                }
                dis.readUnsignedShort(); // attribute byte count
                // add face
                faces.add(triIdx[0]); faces.add(0);
                faces.add(triIdx[1]); faces.add(0);
                faces.add(triIdx[2]); faces.add(0);
            }
        }

        mesh.getPoints().setAll(toFloatArray(points));
        mesh.getTexCoords().setAll(0,0);
        mesh.getFaces().setAll(faces.stream().mapToInt(i->i).toArray());
        return mesh;
    }



    private float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i=0; i<list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private void resetView(MeshView meshView, Rotate rx, Rotate ry) {
        rx.setAngle(-30);
        ry.setAngle(30);
        meshView.setScaleX(5);
        meshView.setScaleY(5);
        meshView.setScaleZ(5);
    }



    private void addMouseControl(MeshView meshView, Scene scene,SubScene subScene,    Stage stage , Rotate rotateX,Rotate rotateY) {
        final double[] mouseOld = new double[2];

        subScene.setOnMousePressed(e -> {
            mouseOld[0] = e.getSceneX();
            mouseOld[1] = e.getSceneY();
        });

        subScene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - mouseOld[0];
            double dy = e.getSceneY() - mouseOld[1];
            rotateY.setAngle(rotateY.getAngle() + dx * 0.5);
            rotateX.setAngle(rotateX.getAngle() - dy * 0.5);
            mouseOld[0] = e.getSceneX();
            mouseOld[1] = e.getSceneY();
            updateStatusBar(rotateX,rotateY);
        });

        subScene.setOnScroll(e -> {
            double delta = e.getDeltaY();
            meshView.setScaleX(meshView.getScaleX() + delta * 0.001);
            meshView.setScaleY(meshView.getScaleY() + delta * 0.001);
            meshView.setScaleZ(meshView.getScaleZ() + delta * 0.001);
        });

    }


    public static void main(String[] args) {
        launch(args);
    }
}
