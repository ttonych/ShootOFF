/*
 * Copyright (c) 2015 phrack. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.shootoff.gui;

import java.io.File;
import java.io.IOException;

import marytts.util.io.FileFilter;

import com.github.sarxos.webcam.Webcam;
import com.shootoff.camera.CameraManager;
import com.shootoff.camera.CamerasSupervisor;
import com.shootoff.config.Configuration;
import com.shootoff.plugins.RandomShoot;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ShootOFFController implements CameraConfigListener, TargetListener {
	private Stage shootOFFStage;
	@FXML private MenuBar mainMenu;
	@FXML private Menu addTargetMenu;
	@FXML private Menu editTargetMenu;
	@FXML private Menu trainingMenu;
	@FXML private ToggleGroup trainingToggleGroup;
	@FXML private TabPane cameraTabPane;
	@FXML private Group defaultCanvasGroup;
	@FXML private TableView<ShotEntry> shotTimerTable;
	
	private CamerasSupervisor camerasSupervisor;
	private Configuration config;
	private final ObservableList<ShotEntry> shotEntries = FXCollections.observableArrayList();
	
	public void init(Configuration config) {
		this.config = config;
		this.camerasSupervisor = new CamerasSupervisor(config);
		
		findTargets();
		registerTrainingProtocols();
		
		if (config.getWebcams().isEmpty()) {
			Webcam defaultCamera = Webcam.getDefault();
			camerasSupervisor.addCameraManager(defaultCamera, 
					new CanvasManager(defaultCanvasGroup, config, shotEntries));
		} else {
			addConfiguredCameras();
		}
		
		shootOFFStage = (Stage)mainMenu.getScene().getWindow();
		shootOFFStage.setOnCloseRequest((value) -> {
			camerasSupervisor.setStreamingAll(false);
		});
		
		TableColumn<ShotEntry, String> timeCol = new TableColumn<ShotEntry, String>("Time");
		timeCol.setPrefWidth(65);
		timeCol.setCellValueFactory(
                new PropertyValueFactory<ShotEntry, String>("timestamp"));
		
		TableColumn<ShotEntry, String> laserCol = new TableColumn<ShotEntry, String>("Laser");
		laserCol.setPrefWidth(65);
		laserCol.setCellValueFactory(
                new PropertyValueFactory<ShotEntry, String>("color"));
       
		shotTimerTable.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<ShotEntry>() {
	        @Override
	        public void onChanged(Change<? extends ShotEntry> change)
	        {
	        	while (change.next()) {
		        	for (ShotEntry unselected : change.getRemoved()) {
		        		unselected.getShot().getMarker().setStroke(Color.BLACK);
		        	}
		        	
		        	for (ShotEntry selected : change.getAddedSubList()) {
		        		selected.getShot().getMarker().setStroke(Color.GOLD);
		        	}
	        	}
	        }
	    });

		shotTimerTable.getColumns().add(timeCol);
		shotTimerTable.getColumns().add(laserCol);
		shotTimerTable.setItems(shotEntries);
	}
	
	@Override
	public void cameraConfigUpdated() {
		addConfiguredCameras();
	}
	
	private void addConfiguredCameras() {
		cameraTabPane.getTabs().clear();
		
		for (Webcam webcam : config.getWebcams()) {
			Tab cameraTab = new Tab(webcam.getName());
			Group cameraCanvasGroup = new Group();
			// 640 x 480
			cameraTab.setContent(new AnchorPane(cameraCanvasGroup));
			
			camerasSupervisor.addCameraManager(webcam, 
					new CanvasManager(cameraCanvasGroup, config, shotEntries));
			
			cameraTabPane.getTabs().add(cameraTab);
		}
	}
	
	private void findTargets() {
		File targetsFolder = new File("targets");
		
		for (File file : targetsFolder.listFiles(new FileFilter("target"))) {
			newTarget(file);
		}
	}
	
	private void registerTrainingProtocols() {
		RadioMenuItem randomShootItem = new RadioMenuItem(new RandomShoot().getInfo().getName());
		randomShootItem.setToggleGroup(trainingToggleGroup);
		
		randomShootItem.setOnAction((e) -> {
				config.setProtocol(new RandomShoot(camerasSupervisor.getTargets()));
			});
		
		trainingMenu.getItems().add(randomShootItem);
	}
	
	@FXML 
	public void clickedNoneProtocol(ActionEvent event) {
		config.setProtocol(null);
	}
	
	@FXML 
	public void preferencesClicked(ActionEvent event) throws IOException {
		FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("com/shootoff/gui/Preferences.fxml"));
		loader.load();
		
		Stage preferencesStage = new Stage();
		
		preferencesStage.initOwner(shootOFFStage);
		preferencesStage.initModality(Modality.WINDOW_MODAL);
        preferencesStage.setTitle("Preferences");
        preferencesStage.setScene(new Scene(loader.getRoot()));
        preferencesStage.show();
        ((PreferencesController)loader.getController()).setConfig(config, this);
    }
	
	@FXML 
	public void toggleArenaClicked(ActionEvent event) throws IOException {
		new ProjectorArenaController().toggleArena();
    }
	
	@FXML
	public void exitMenuClicked(ActionEvent event) {
		camerasSupervisor.setStreamingAll(false);
		shootOFFStage.close();
	}

	@FXML 
	public void createTargetMenuClicked(ActionEvent event) throws IOException {
		FXMLLoader loader = createPreferencesStage();
		
        CameraManager currentCamera = camerasSupervisor.getCameraManager(cameraTabPane.getSelectionModel().getSelectedIndex());
		Image currentFrame = currentCamera.getCurrentFrame();
        ((TargetEditorController)loader.getController()).init(currentFrame, this);
	}
	
	private FXMLLoader createPreferencesStage() throws IOException {
		FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("com/shootoff/gui/TargetEditor.fxml"));
		loader.load();
		
		Stage preferencesStage = new Stage();
		
		preferencesStage.initOwner(shootOFFStage);
		preferencesStage.initModality(Modality.WINDOW_MODAL);
        preferencesStage.setTitle("TargetEditor");
        preferencesStage.setScene(new Scene(loader.getRoot()));
        preferencesStage.show();
        
        return loader;
	}
	
	@FXML
	public void resetClicked(ActionEvent event) {
		camerasSupervisor.reset();
		if (config.getProtocol().isPresent()) config.getProtocol().get().reset(camerasSupervisor.getTargets());
	}

	@Override
	public void newTarget(File path) {
		String targetPath = path.getPath();
		
		String targetName = targetPath.substring(targetPath.lastIndexOf(File.separator) + 1,
				targetPath.lastIndexOf('.'));
		
		MenuItem addTargetItem = new MenuItem(targetName);
		addTargetItem.setMnemonicParsing(false);
		
		addTargetItem.setOnAction((e) -> {
				camerasSupervisor.getCanvasManager(
						cameraTabPane.getSelectionModel().getSelectedIndex()).addTarget(path);
			});
		
		MenuItem editTargetItem = new MenuItem(targetName);
		editTargetItem.setMnemonicParsing(false);
		
		editTargetItem.setOnAction((e) -> {
				try {
					FXMLLoader loader = createPreferencesStage();
					
					CameraManager currentCamera = camerasSupervisor.getCameraManager(cameraTabPane.getSelectionModel().getSelectedIndex());
					Image currentFrame = currentCamera.getCurrentFrame();
					((TargetEditorController)loader.getController()).init(currentFrame, this, path);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});
		
		addTargetMenu.getItems().add(addTargetItem);
		editTargetMenu.getItems().add(editTargetItem);
	}
}
