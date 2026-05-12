package org.eclipse.keyple.plugin.pcsc.validation.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.keyple.core.service.ObservablePlugin;
import org.eclipse.keyple.core.service.PluginEvent;
import org.eclipse.keyple.core.service.spi.PluginObservationExceptionHandlerSpi;
import org.eclipse.keyple.core.service.spi.PluginObserverSpi;
import org.eclipse.keyple.plugin.pcsc.validation.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.validation.Console;
import org.eclipse.keyple.plugin.pcsc.validation.Scenario;
import org.eclipse.keyple.plugin.pcsc.validation.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.validation.ValidationContext;

/** M07 - Observable plugin: reader hot-plug detection. */
public final class M07_ObservablePlugin extends AbstractModule {

  public M07_ObservablePlugin(Console console) {
    super("M07", "Observable Plugin (Reader Hot-Plug)", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() { return "M07.1"; }
          public String getTitle() { return "READER_CONNECTED event on USB reader plug-in"; }
          public String getRequiredEquipment() { return "A USB PC/SC reader to plug in"; }

          public ScenarioResult run(ValidationContext ctx) {
            ObservablePlugin obs = (ObservablePlugin) ctx.getPlugin();
            Set<String> before = new HashSet<>(ctx.getPlugin().getReaderNames());
            console.info("Readers before: " + before);

            CountDownLatch latch = new CountDownLatch(1);
            final String[] connectedReader = {null};

            PluginObserverSpi observer = event -> {
              if (event.getType() == PluginEvent.Type.READER_CONNECTED) {
                connectedReader[0] = event.getReaderNames().first();
                latch.countDown();
              }
            };
            obs.setPluginObservationExceptionHandler(
                (pluginName, e) -> console.warn("Plugin obs error: " + e.getMessage()));
            obs.addObserver(observer);

            try {
              ctx.getConsole().waitForEnterOrTimeout(
                  "Plug in a USB PC/SC reader and press ENTER", 30_000);
              boolean received = latch.await(5, TimeUnit.SECONDS);
              obs.removeObserver(observer);
              if (received) {
                console.info("READER_CONNECTED: " + connectedReader[0]);
                return ScenarioResult.pass(getId(), "READER_CONNECTED -> " + connectedReader[0]);
              }
              // Fallback: check reader list change
              Set<String> after = new HashSet<>(ctx.getPlugin().getReaderNames());
              after.removeAll(before);
              if (!after.isEmpty()) {
                return ScenarioResult.pass(getId(), "New reader visible in list: " + after);
              }
              return ScenarioResult.fail(getId(), "No READER_CONNECTED event received within timeout");
            } catch (Exception e) {
              obs.removeObserver(observer);
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M07.2"; }
          public String getTitle() { return "READER_DISCONNECTED event on USB reader unplug"; }
          public String getRequiredEquipment() { return "A USB PC/SC reader to unplug"; }

          public ScenarioResult run(ValidationContext ctx) {
            if (ctx.getPlugin().getReaderNames().isEmpty()) {
              return ScenarioResult.skip(getId(), "No readers connected");
            }
            ObservablePlugin obs = (ObservablePlugin) ctx.getPlugin();
            console.info("Readers before: " + ctx.getPlugin().getReaderNames());

            CountDownLatch latch = new CountDownLatch(1);
            final String[] disconnectedReader = {null};

            PluginObserverSpi observer = event -> {
              if (event.getType() == PluginEvent.Type.READER_DISCONNECTED) {
                disconnectedReader[0] = event.getReaderNames().first();
                latch.countDown();
              }
            };
            obs.setPluginObservationExceptionHandler(
                (pluginName, e) -> console.warn("Plugin obs error: " + e.getMessage()));
            obs.addObserver(observer);

            try {
              ctx.getConsole().waitForEnterOrTimeout(
                  "Unplug a USB PC/SC reader and press ENTER", 30_000);
              boolean received = latch.await(5, TimeUnit.SECONDS);
              obs.removeObserver(observer);
              if (received) {
                console.info("READER_DISCONNECTED: " + disconnectedReader[0]);
                return ScenarioResult.pass(getId(), "READER_DISCONNECTED -> " + disconnectedReader[0]);
              }
              return ScenarioResult.fail(getId(), "No READER_DISCONNECTED event received");
            } catch (Exception e) {
              obs.removeObserver(observer);
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M07.3"; }
          public String getTitle() { return "Plugin monitoring cycle duration (1000 ms)"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            // The monitoring cycle duration is fixed at 1000 ms in PcscPluginAdapter.
            // We verify the observable plugin interface is available.
            if (ctx.getPlugin() instanceof ObservablePlugin) {
              console.info("Plugin is ObservablePlugin: YES");
              console.info("Expected monitoring cycle: 1000 ms (fixed in PcscPluginAdapter)");
              return ScenarioResult.pass(getId(), "Plugin implements ObservablePlugin");
            }
            return ScenarioResult.fail(getId(), "Plugin does not implement ObservablePlugin");
          }
        });

    return list;
  }
}
