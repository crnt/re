/*******************************************************************************
 * Copyright (c) 2014, MD PnP Program
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package org.mdpnp.apps.testapp.pca;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import org.mdpnp.apps.fxbeans.InfusionStatusFx;
import org.mdpnp.apps.fxbeans.InfusionStatusFxList;
import org.mdpnp.apps.testapp.DeviceListModel;
import org.mdpnp.apps.testapp.InfusionStatusFxListCell;
import org.mdpnp.apps.testapp.vital.Vital;
import org.mdpnp.apps.testapp.vital.VitalModel;
import org.mdpnp.apps.testapp.vital.VitalModel.State;
import org.mdpnp.rtiapi.data.EventLoop;
import org.mdpnp.rtiapi.data.QosProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rti.dds.infrastructure.InstanceHandle_t;
import com.rti.dds.subscription.Subscriber;

/**
 * @author Jeff Plourde
 *
 */
public class PCAConfig implements ListChangeListener<Vital> {

    @FXML protected ListView<InfusionStatusFx> pumpList;
    @FXML protected TextArea warningStatus, infusionStatus;
    @FXML protected ComboBox<Integer> warningsToAlarm;
    @FXML protected ComboBox<VitalSign> vitalSigns;
    @FXML protected VBox vitalsPanel;
    private static final Logger log = LoggerFactory.getLogger(PCAConfig.class);

    
    private ice.InfusionObjectiveDataWriter objectiveWriter;

    
    
    public PCAConfig set(ScheduledExecutorService executor, ice.InfusionObjectiveDataWriter objectiveWriter, DeviceListModel deviceListModel) {
        controls.visibleProperty().bind(configure.selectedProperty());
        this.objectiveWriter = objectiveWriter;
        pumpList.setCellFactory(new Callback<ListView<InfusionStatusFx>, ListCell<InfusionStatusFx>>() {

            @Override
            public ListCell<InfusionStatusFx> call(ListView<InfusionStatusFx> param) {
                return new InfusionStatusFxListCell(deviceListModel);
            }
            
        });
        List<Integer> values = new ArrayList<Integer>();
        for (int i = 0; i < VitalSign.values().length; i++) {
            values.add(i + 1);
        }
        warningsToAlarm.setItems(FXCollections.observableList(values));
        vitalSigns.setItems(FXCollections.observableArrayList(VitalSign.values()));
        
        pumpList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<InfusionStatusFx>() {

            @Override
            public void changed(ObservableValue<? extends InfusionStatusFx> observable, InfusionStatusFx oldValue, InfusionStatusFx newValue) {
                infusionStatus.textProperty().unbind();
                if(null != newValue) {
                    infusionStatus.textProperty().bind(Bindings.when(newValue.infusionActiveProperty()).then("ACTIVE").otherwise("INACTIVE"));
                } else {
                    infusionStatus.textProperty().set("Select an infusion");
                }
            }
            
        });
        
        return this;
    }
    
    @FXML public void warningsToAlarmSet(ActionEvent evt) {
        if(model != null) {
            model.setCountWarningsBecomeAlarm(warningsToAlarm.getSelectionModel().getSelectedItem());
        }
    }
    
   
    @FXML public void addVitalSign(ActionEvent evt) {
        ((VitalSign) vitalSigns.getSelectionModel().getSelectedItem()).addToModel(model);
    }
    
    public PCAConfig() {
        pumpModel = new InfusionStatusFxList(ice.InfusionStatusTopic.VALUE);
    }



    private VitalModel model;
    private final InfusionStatusFxList pumpModel;
    @FXML Button add;
    @FXML FlowPane controls;
    @FXML CheckBox configure;
    @FXML TextArea interlockStatus;

    protected void updateVitals() {
        if(Platform.isFxApplicationThread()) {
            _updateVitals();
        } else {
            Platform.runLater(new Runnable() {
                public void run() {
                    _updateVitals();
                }
            });
        }
    }


    protected void _updateVitals() {
        Map<Vital, Node> existentJVitals = new HashMap<Vital, Node>();
        
        for(Iterator<Node> itr = vitalsPanel.getChildren().iterator(); itr.hasNext();) {
            Node n = itr.next();
            existentJVitals.put((Vital)n.getUserData(), n);
            itr.remove();
        }

        final VitalModel model = this.model;
        if (model != null) {
            for( Iterator<Vital> itr = model.iterator(); itr.hasNext(); ) {
                final Vital vital = itr.next();

                Node jVital = existentJVitals.get(vital);
                if(null != jVital) {
                    vitalsPanel.getChildren().add(jVital);
                } else {
                    FXMLLoader loader = new FXMLLoader(VitalView.class.getResource("VitalView.fxml"));
                    try {
                        jVital = loader.load();
                    } catch (IOException e) {
                        log.warn("",e);
                        continue;
                    }
                    VitalView view = loader.getController();
                    view.set(vital, configure.selectedProperty());
                }
                vitalsPanel.getChildren().add(jVital);
            }
        }
    }

    public void start(Subscriber subscriber, EventLoop eventLoop) {
        pumpList.setItems(pumpModel);
        pumpModel.start(subscriber, eventLoop, null, null, QosProfiles.ice_library, QosProfiles.state);
    }
    
    public void stop() {
        pumpModel.stop();
    }
    
    public void setModel(VitalModel model) {
        if (this.model != null) {
            this.model.removeListener(this);
        }
        this.model = model;
        
        if (this.model != null) {
            this.model.addListener(this);
        }
        updateVitals();
        if(model != null) {
            warningsToAlarm.getSelectionModel().select(model.getCountWarningsBecomeAlarm());
            warningStatus.textProperty().bind(model.warningTextProperty());
            interlockStatus.textProperty().bind(model.interlockTextProperty());
            // TODO Bind up the warnings to alarms dropdown 
            model.isInfusionStoppedProperty().addListener(new ChangeListener<Boolean>() {

                @Override
                public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                    InfusionStatusFx p = pumpList.getSelectionModel().getSelectedItem();
                    if(null != p) {
                        setStop(p, newValue);
                    }
                }
                
            });
            
            model.stateProperty().addListener(new ChangeListener<VitalModel.State>() {

                @Override
                public void changed(ObservableValue<? extends State> observable, State oldValue, State newValue) {
                    switch(newValue) {
                    case Alarm:
                        warningStatus.setBackground(new Background(new BackgroundFill(Color.RED, null, null)));
                        break;
                    case Warning:
                        warningStatus.setBackground(new Background(new BackgroundFill(Color.YELLOW, null, null)));
                        break;
                    case Normal:
                        warningStatus.setBackground(new Background(new BackgroundFill(null, null, null)));
                        break;
                    default:
                        break;
                    }
                }
                
            });
        } else {
            vitalsPanel.getChildren().clear();
            warningStatus.textProperty().unbind();
            interlockStatus.textProperty().unbind();
        }
        
        
    }

    public void setStop(InfusionStatusFx status, boolean stop) {
        ice.InfusionObjective obj = new ice.InfusionObjective();
        obj.requestor = "ME";
        obj.unique_device_identifier = status.getUnique_device_identifier();
        obj.stopInfusion = stop;
        objectiveWriter.write(obj, InstanceHandle_t.HANDLE_NIL);
    }

    @Override
    public void onChanged(javafx.collections.ListChangeListener.Change<? extends Vital> c) {
        while(c.next()) {
            if(c.wasAdded() || c.wasRemoved()) {
                updateVitals();
            }
        }
    }

    @FXML public void interlockStatusClicked(MouseEvent event) {
        if(model != null) {
            model.resetInfusion();
        }
    }
}
