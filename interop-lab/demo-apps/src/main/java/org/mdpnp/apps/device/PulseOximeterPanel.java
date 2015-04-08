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
package org.mdpnp.apps.device;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Set;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import org.mdpnp.apps.fxbeans.NumericFx;
import org.mdpnp.apps.fxbeans.SampleArrayFx;
import org.mdpnp.guis.waveform.NumericWaveformSource;
import org.mdpnp.guis.waveform.SampleArrayWaveformSource;
import org.mdpnp.guis.waveform.TestWaveformSource;
import org.mdpnp.guis.waveform.WaveformPanel;
import org.mdpnp.guis.waveform.WaveformPanelFactory;
import org.mdpnp.guis.waveform.javafx.JavaFXWaveformPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.javafx.tk.FontMetrics;
import com.sun.javafx.tk.Toolkit;

/**
 * @author Jeff Plourde
 *
 */
public class PulseOximeterPanel extends DevicePanel {

    @SuppressWarnings("unused")
    private Label spo2, heartrate, spo2Label, heartrateLabel;
    private Label spo2Low, spo2Up, heartrateLow, heartrateUp;
    private GridPane spo2Bounds, heartrateBounds;
    private BorderPane spo2Panel, heartratePanel;
    private WaveformPanel pulsePanel, plethPanel;
    private Label time;
//    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    protected void buildComponents() {
        spo2Bounds = new GridPane();
        spo2Bounds.add(spo2Up = new Label("--"), 0, 0);
        spo2Bounds.add(spo2Low = new Label("--"), 0, 1);
        spo2Bounds.add(spo2Label = new Label("%"), 0, 2);
        spo2Up.setVisible(false);
        spo2Low.setVisible(false);

        spo2Panel = new BorderPane();
        spo2Panel.setTop(new Label("SpO\u2082"));
        spo2Panel.setCenter(spo2 = new Label("----"));
        spo2.setAlignment(Pos.CENTER_RIGHT);
        spo2Panel.setRight(spo2Bounds);

        heartrateBounds = new GridPane();
        heartrateBounds.add(heartrateUp = new Label("--"), 0, 0);
        heartrateBounds.add(heartrateLow = new Label("--"), 0, 1);
        heartrateBounds.add(heartrateLabel = new Label("BPM"), 0, 2);
        heartrateUp.setVisible(false);
        heartrateLow.setVisible(false);

        heartratePanel = new BorderPane();
        Label lbl;
        heartratePanel.setTop(lbl = new Label("Pulse Rate"));
        FontMetrics fm = Toolkit.getToolkit().getFontLoader().getFontMetrics(lbl.getFont());
        float w = fm.computeStringWidth("RespiratoryRate");
        lbl.setMinWidth(w);
        lbl.setPrefWidth(w);
        heartratePanel.setCenter(heartrate = new Label("----"));
        heartrate.setTextAlignment(TextAlignment.RIGHT);
        heartrate.setAlignment(Pos.CENTER_RIGHT);
        heartratePanel.setRight(heartrateBounds);

        WaveformPanelFactory fact = new WaveformPanelFactory();

        plethPanel = fact.createWaveformPanel();
        pulsePanel = fact.createWaveformPanel();

        GridPane upper = new GridPane();
        BorderPane x = label("Plethysmogram", (Node) plethPanel);
        GridPane.setVgrow(x, Priority.ALWAYS);
        GridPane.setHgrow(x, Priority.ALWAYS);
        upper.add(x, 0, 0);
        x = label("Pulse Rate", (Node) pulsePanel);
        GridPane.setVgrow(x, Priority.ALWAYS);
        GridPane.setHgrow(x, Priority.ALWAYS);
        upper.add(x, 0, 1);

        GridPane east = new GridPane();
        east.add(spo2Panel, 0, 0);
        east.add(heartratePanel, 0, 1);

        setCenter(upper);
        setRight(east);

        setBottom(labelLeft("Last Sample: ", time = new Label("TIME")));

        ((JavaFXWaveformPane)plethPanel).getCanvas().getGraphicsContext2D().setStroke(Color.CYAN);
        ((JavaFXWaveformPane)pulsePanel).getCanvas().getGraphicsContext2D().setStroke(Color.CYAN);
    }

    public PulseOximeterPanel() {
        getStyleClass().add("pulse-oximeter-panel");
        buildComponents();
        plethPanel.setSource(plethWave);
        pulsePanel.setSource(pulseWave);
        
        plethPanel.start();
        pulsePanel.start();
    }

    private SampleArrayWaveformSource plethWave;
    private NumericWaveformSource pulseWave;

    @Override
    public void destroy() {
        plethPanel.setSource(null);
        pulsePanel.setSource(null);
        plethPanel.stop();
        pulsePanel.stop();
        
        if(deviceMonitor != null) {
            deviceMonitor.getNumericModel().removeListener(numericListener);
            deviceMonitor.getSampleArrayModel().removeListener(sampleArrayListener);
        }
        super.destroy();
    }

    public static boolean supported(Set<String> names) {
        return names.contains(rosetta.MDC_PULS_OXIM_SAT_O2.VALUE) && names.contains(rosetta.MDC_PULS_OXIM_PULS_RATE.VALUE);// &&
    }

    @Override
    public void set(DeviceDataMonitor deviceMonitor) {
        super.set(deviceMonitor);
        deviceMonitor.getNumericModel().addListener(numericListener);
        deviceMonitor.getNumericModel().forEach((t)->add(t));
        
        deviceMonitor.getSampleArrayModel().addListener(sampleArrayListener);
        deviceMonitor.getSampleArrayModel().forEach((t)->add(t));
    }
    
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(PulseOximeterPanel.class);
    
    protected void add(NumericFx data) {
        if(!time.textProperty().isBound()) {
            time.textProperty().bind(data.presentation_timeProperty().asString());
        }
        
        if (rosetta.MDC_PULS_OXIM_PULS_RATE.VALUE.equals(data.getMetric_id())) {
            heartrate.textProperty().bind(data.valueProperty().asString("%.0f"));
            if(null == pulseWave) {
                pulseWave = new NumericWaveformSource(deviceMonitor.getNumericList().getReader(), data.getHandle());
                pulsePanel.setSource(pulseWave);
            }
        } else if(rosetta.MDC_PULS_OXIM_SAT_O2.VALUE.equals(data.getMetric_id())) {
            spo2.textProperty().bind(data.valueProperty().asString("%.0f"));
        }
    }
    
    protected void remove(NumericFx data) {
        // TODO this is flaky
        time.textProperty().unbind();
        if (rosetta.MDC_PULS_OXIM_PULS_RATE.VALUE.equals(data.getMetric_id())) {
            heartrate.textProperty().unbind();
            if(null != pulseWave) {
                pulsePanel.setSource(null);
                pulseWave = null;
            }
        } else if(rosetta.MDC_PULS_OXIM_SAT_O2.VALUE.equals(data.getMetric_id())) {
            spo2.textProperty().unbind();
        }
    }

    private final OnListChange<NumericFx> numericListener = new OnListChange<NumericFx>(
            (t)->add(t), null, (t)->remove(t));
    
    protected void add(SampleArrayFx data) {
        if (rosetta.MDC_PULS_OXIM_PLETH.VALUE.equals(data.getMetric_id())) {
            if(null == plethWave) {
                plethWave = new SampleArrayWaveformSource(deviceMonitor.getSampleArrayList().getReader(), data.getHandle());
                plethPanel.setSource(plethWave);
            }
        }
    }
    protected void remove(SampleArrayFx data) {
        if (rosetta.MDC_PULS_OXIM_PLETH.VALUE.equals(data.getMetric_id())) {
            if(null != plethWave) {
                plethPanel.setSource(null);
                plethWave = null;
            }
        }
    }

    
    private final OnListChange<SampleArrayFx> sampleArrayListener = new OnListChange<SampleArrayFx>(
            (t)->add(t), null, (t)->remove(t));

    
    public static class MainApp extends Application {

        @Override
        public void start(Stage primaryStage) throws Exception {
            PulseOximeterPanel p = new PulseOximeterPanel();
            p.plethPanel.setSource(new TestWaveformSource());
            primaryStage.setScene(new Scene(p));
            primaryStage.show();
        }
        
    }
    
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
