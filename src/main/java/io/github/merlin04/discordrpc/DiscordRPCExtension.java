package io.github.merlin04.discordrpc;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.SettableEnumValue;
import com.bitwig.extension.controller.api.StringValue;

public class DiscordRPCExtension extends ControllerExtension {
   private static final String[] enabledOpts = { "On", "Off" };
   private static final long APP_ID = 1306910213659561984L;
   
   private SettableEnumValue enabled;
   private final ControllerHost host;
   private SimpleDiscordIPC client;
   private String bitwigVersion;
   
   private StringValue projectName;
   private StringValue panelLayout;
   private long projectOpenedTime;
   private boolean isDisconnected = true;
   private int observerCallCount = 0;

   protected DiscordRPCExtension(final DiscordRPCExtensionDefinition definition, final ControllerHost host) {
      super(definition, host);
      this.host = host;
      
      // Get Bitwig version
      try {
         bitwigVersion = "Bitwig Studio " + host.getHostVersion();
      } catch (Exception e) {
         bitwigVersion = "Bitwig Studio";
      }
      
      host.errorln("========================================");
      host.errorln("DiscordRPC: Constructor called");
      host.errorln("DiscordRPC: Extension Version 0.3.0 (Cross-Platform)");
      host.errorln("DiscordRPC: Detected: " + bitwigVersion);
      host.errorln("========================================");
   }

   private void disconnect() {
      host.errorln("DiscordRPC: disconnect() called");
      try {
         if (client != null) {
            client.close();
            host.errorln("DiscordRPC: Client closed successfully");
         }
         isDisconnected = true;
         
      } catch (Exception e) {
         host.errorln("DiscordRPC: ERROR in disconnect(): " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void updatePresence() {
      host.errorln("DiscordRPC: updatePresence() called");
      
      try {
         if (!this.enabled.get().equals(enabledOpts[0])) {
            host.errorln("DiscordRPC: Extension is disabled, skipping update");
            return;
         }
         
         String pn = this.projectName.get();
         host.errorln("DiscordRPC: Current project name: '" + pn + "'");
         
         if (pn == null || pn.equals("")) {
            host.errorln("DiscordRPC: No project name, disconnecting if needed");
            if (!isDisconnected)
               this.disconnect();
            return;
         }
         
         if (isDisconnected) {
            host.errorln("DiscordRPC: Was disconnected, reconnecting...");
            this.discordConnect();
         }
         
         String panel = this.panelLayout.get();
         host.errorln("DiscordRPC: Current panel layout: '" + panel + "'");
         
         // Robustere Panel-Erkennung für Bitwig 6
         String panelState = "";
         if (panel != null && !panel.isEmpty()) {
            String panelLower = panel.toLowerCase();
            host.errorln("DiscordRPC: Panel lowercase: '" + panelLower + "'");
            
            if (panelLower.contains("arrang")) {
               panelState = "Arranging";
            } else if (panelLower.contains("mix")) {
               panelState = "Mixing";
            } else if (panelLower.contains("edit")) {
               panelState = "Editing";
            } else {
               panelState = "In " + panel;
            }
         }
         
         host.errorln("DiscordRPC: Final panel state: '" + panelState + "'");
         
         // Füge Bitwig Version zur state-Zeile hinzu
         String stateWithVersion = panelState;
         if (!panelState.isEmpty()) {
            // Extrahiere nur die Hauptversion (z.B. "6" aus "6.0 Beta 7")
            String shortVersion = bitwigVersion.replace("Bitwig Studio ", "");
            // Nimm nur die erste Ziffer/Nummer
            if (shortVersion.matches(".*\\d+.*")) {
               shortVersion = shortVersion.split("[^0-9]")[0];
               if (!shortVersion.isEmpty()) {
                  stateWithVersion = panelState + " • Bitwig " + shortVersion;
               }
            }
         } else {
            stateWithVersion = bitwigVersion;
         }
         
         host.errorln("DiscordRPC: Final state with version: '" + stateWithVersion + "'");
         host.errorln("DiscordRPC: Building presence with:");
         host.errorln("  - details: " + pn + ".bwproject");
         host.errorln("  - state: " + panelState);
         host.errorln("  - version: " + bitwigVersion);
         host.errorln("  - timestamp: " + projectOpenedTime);
         
         host.errorln("DiscordRPC: Calling updatePresence()...");
         client.updatePresence(
            pn + ".bwproject",
            stateWithVersion,  // "Arranging • Bitwig 6"
            "bitwig_studio_logo_rgb",
            bitwigVersion,  // Hover-Text bleibt vollständig
            projectOpenedTime
         );
         host.errorln("DiscordRPC: updatePresence() completed successfully");
         
      } catch (Exception e) {
         host.errorln("DiscordRPC: ERROR in updatePresence(): " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void updateTimestamp() {
      projectOpenedTime = System.currentTimeMillis() / 1000L;
      host.errorln("DiscordRPC: Timestamp updated to: " + projectOpenedTime);
   }

   private void discordConnect() {
      host.errorln("DiscordRPC: discordConnect() called");

      try {
         client = new SimpleDiscordIPC(APP_ID);

         host.errorln("DiscordRPC: Connecting to Discord with Application ID: " + APP_ID);
         host.errorln("DiscordRPC: OS: " + System.getProperty("os.name"));
         host.errorln("DiscordRPC: OS Version: " + System.getProperty("os.version"));
         host.errorln("DiscordRPC: Java Version: " + System.getProperty("java.version"));
         host.errorln("DiscordRPC: Temp Dir: " + System.getProperty("java.io.tmpdir"));
         
         host.errorln("DiscordRPC: Calling client.connect()...");
         client.connect();
         
         isDisconnected = false;
         host.errorln("=======================================");
         host.errorln("DiscordRPC: *** CONNECTION SUCCESSFUL ***");
         host.errorln("DiscordRPC: Discord connection is WORKING!");
         host.errorln("=======================================");
         
      } catch (Exception e) {
         host.errorln("DiscordRPC: FATAL ERROR in discordConnect(): " + e.getMessage());
         host.errorln("DiscordRPC: Is Discord running?");
         e.printStackTrace();
      }
   }

   @Override
   public void init() {
      host.errorln("========================================");
      host.errorln("DiscordRPC: init() method called");
      host.errorln("========================================");
      
      try {
         host.errorln("DiscordRPC: Checking API version...");
         host.errorln("DiscordRPC: Host class: " + host.getClass().getName());
         
         host.errorln("DiscordRPC: Attempting initial connection...");
         this.discordConnect();

         host.errorln("DiscordRPC: Getting DocumentState...");
         DocumentState documentState = host.getDocumentState();
         host.errorln("DiscordRPC: DocumentState obtained: " + (documentState != null ? "OK" : "NULL"));
         
         host.errorln("DiscordRPC: Creating Application...");
         Application app = host.createApplication();
         host.errorln("DiscordRPC: Application created: " + (app != null ? "OK" : "NULL"));

         host.errorln("DiscordRPC: Setting up projectName observer...");
         projectName = app.projectName();
         host.errorln("DiscordRPC: projectName StringValue obtained: " + (projectName != null ? "OK" : "NULL"));
         
         // WICHTIG: Observer ZUERST hinzufügen, dann erst .get() aufrufen!
         projectName.addValueObserver(_value -> {
            observerCallCount++;
            host.errorln("========================================");
            host.errorln("DiscordRPC: PROJECT NAME OBSERVER #" + observerCallCount + " FIRED!");
            host.errorln("DiscordRPC: New value: '" + _value + "'");
            host.errorln("========================================");
            updateTimestamp();
            updatePresence();
         });
         host.errorln("DiscordRPC: projectName observer added");
         host.errorln("DiscordRPC: Initial projectName value: '" + (projectName != null ? projectName.get() : "NULL") + "'");
         
         host.errorln("DiscordRPC: Setting up panelLayout observer...");
         panelLayout = app.panelLayout();
         host.errorln("DiscordRPC: panelLayout StringValue obtained: " + (panelLayout != null ? "OK" : "NULL"));
         
         // WICHTIG: Observer ZUERST hinzufügen, dann erst .get() aufrufen!
         panelLayout.addValueObserver(_value -> {
            observerCallCount++;
            host.errorln("========================================");
            host.errorln("DiscordRPC: PANEL LAYOUT OBSERVER #" + observerCallCount + " FIRED!");
            host.errorln("DiscordRPC: New value: '" + _value + "'");
            host.errorln("========================================");
            updatePresence();
         });
         host.errorln("DiscordRPC: panelLayout observer added");
         host.errorln("DiscordRPC: Initial panelLayout value: '" + (panelLayout != null ? panelLayout.get() : "NULL") + "'");
         
         host.errorln("DiscordRPC: Setting up enabled setting...");
         enabled = documentState.getEnumSetting("Enabled", "opts", enabledOpts, enabledOpts[0]);
         host.errorln("DiscordRPC: enabled setting created: " + (enabled != null ? "OK" : "NULL"));
         
         // WICHTIG: Observer ZUERST hinzufügen, dann erst .get() aufrufen!
         enabled.addValueObserver(value -> {
            observerCallCount++;
            host.errorln("========================================");
            host.errorln("DiscordRPC: ENABLED OBSERVER #" + observerCallCount + " FIRED!");
            host.errorln("DiscordRPC: New value: '" + value + "'");
            host.errorln("========================================");
            
            if (value.equals(enabledOpts[0])) {
               host.errorln("DiscordRPC: Extension turned ON, updating presence");
               updateTimestamp();
               this.updatePresence();
            } else {
               host.errorln("DiscordRPC: Extension turned OFF, disconnecting");
               this.disconnect();
            }
         });
         host.errorln("DiscordRPC: enabled observer added");
         host.errorln("DiscordRPC: Initial enabled value: '" + (enabled != null ? enabled.get() : "NULL") + "'");
         
         host.errorln("========================================");
         host.errorln("DiscordRPC: init() completed successfully!");
         host.errorln("DiscordRPC: Waiting for observers to fire...");
         host.errorln("========================================");
         
      } catch (Exception e) {
         host.errorln("DiscordRPC: FATAL ERROR in init(): " + e.getMessage());
         host.errorln("DiscordRPC: Stack trace:");
         e.printStackTrace();
      }
   }

   @Override
   public void exit() {
      host.errorln("DiscordRPC: exit() called");
      this.disconnect();
   }

   @Override
   public void flush() {
      // Nothing to do - our IPC is synchronous
   }
}
