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
   private static final long UPDATE_COOLDOWN_MS = 5000; // 5 Sekunden zwischen Updates

   private SettableEnumValue enabled;
   private final ControllerHost host;
   private SimpleDiscordIPC client;
   private String bitwigVersion;

   private StringValue projectName;
   private StringValue panelLayout;
   private long projectOpenedTime;
   private boolean isDisconnected = true;
   private long lastUpdateTime = 0;

   protected DiscordRPCExtension(final DiscordRPCExtensionDefinition definition, final ControllerHost host) {
      super(definition, host);
      this.host = host;

      // Get Bitwig version
      try {
         bitwigVersion = "Bitwig Studio " + host.getHostVersion();
      } catch (Exception e) {
         bitwigVersion = "Bitwig Studio";
      }

      host.println("DiscordRPC v0.3.1 - " + bitwigVersion);
   }

   private void disconnect() {
      try {
         if (client != null) {
            client.close();
         }
         isDisconnected = true;
      } catch (Exception e) {
         host.errorln("DiscordRPC: Disconnect error - " + e.getMessage());
      }
   }

   private void updatePresence() {
      try {
         // Rate limiting: Max 1 Update alle 5 Sekunden
         long now = System.currentTimeMillis();
         if (now - lastUpdateTime < UPDATE_COOLDOWN_MS) {
            return; // Zu früh, skip
         }
         lastUpdateTime = now;

         if (!this.enabled.get().equals(enabledOpts[0])) {
            return;
         }

         String pn = this.projectName.get();

         if (pn == null || pn.equals("")) {
            if (!isDisconnected)
               this.disconnect();
            return;
         }

         if (isDisconnected) {
            this.discordConnect();
         }

         // Get panel state
         String panel = this.panelLayout.get();
         String panelState = "";

         if (panel != null) {
            String panelLower = panel.toLowerCase();
            if (panelLower.contains("arrange")) {
               panelState = "Arranging";
            } else if (panelLower.contains("mix")) {
               panelState = "Mixing";
            } else if (panelLower.contains("edit")) {
               panelState = "Editing";
            }
         }

         // Füge Bitwig Version zur state-Zeile hinzu
         String stateWithVersion = panelState;
         if (!panelState.isEmpty()) {
            String shortVersion = bitwigVersion.replace("Bitwig Studio ", "");
            if (shortVersion.matches(".*\\d+.*")) {
               shortVersion = shortVersion.split("[^0-9]")[0];
               if (!shortVersion.isEmpty()) {
                  stateWithVersion = panelState + " • Bitwig " + shortVersion;
               }
            }
         } else {
            stateWithVersion = bitwigVersion;
         }

         client.updatePresence(
               pn + ".bwproject",
               stateWithVersion,
               "bitwig_studio_logo_rgb",
               bitwigVersion,
               projectOpenedTime);

      } catch (Exception e) {
         host.errorln("DiscordRPC: Update error - " + e.getMessage());
      }
   }

   private void discordConnect() {
      try {
         if (client != null) {
            client.close();
         }

         client = new SimpleDiscordIPC(APP_ID);
         client.connect();

         projectOpenedTime = System.currentTimeMillis() / 1000;
         isDisconnected = false;

         host.println("DiscordRPC: Connected");

      } catch (Exception e) {
         host.errorln("DiscordRPC: Connection failed - " + e.getMessage());
         isDisconnected = true;
      }
   }

   @Override
   public void init() {
      try {
         this.discordConnect();

         DocumentState documentState = host.getDocumentState();
         Application app = host.createApplication();

         this.projectName = app.projectName();
         this.projectName.addValueObserver(value -> {
            projectOpenedTime = System.currentTimeMillis() / 1000; // Timestamp nur bei Projekt-Wechsel
            updatePresence();
         });

         this.panelLayout = app.panelLayout();
         this.panelLayout.addValueObserver(value -> {
            updatePresence();
         });

         this.enabled = host.getPreferences().getEnumSetting("Enabled", "Settings", enabledOpts, enabledOpts[0]);
         this.enabled.addValueObserver(value -> {
            if (value.equals(enabledOpts[1])) {
               disconnect();
            } else {
               updatePresence();
            }
         });

      } catch (Exception e) {
         host.errorln("DiscordRPC: Init error - " + e.getMessage());
      }
   }

   @Override
   public void exit() {
      try {
         disconnect();
         host.println("DiscordRPC: Stopped");
      } catch (Exception e) {
         host.errorln("DiscordRPC: Exit error - " + e.getMessage());
      }
   }

   @Override
   public void flush() {
      // Not needed
   }
}
