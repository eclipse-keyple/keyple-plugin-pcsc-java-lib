package org.eclipse.keyple.plugin.pcsc.validation;

import java.util.Arrays;
import java.util.List;
import org.eclipse.keyple.core.service.Plugin;
import org.eclipse.keyple.core.service.SmartCardService;
import org.eclipse.keyple.core.service.SmartCardServiceProvider;
import org.eclipse.keyple.plugin.pcsc.PcscPluginFactoryBuilder;
import org.eclipse.keyple.plugin.pcsc.validation.module.*;

/** Entry point for the Keyple PC/SC Plugin Validation Tool. */
public final class ValidationApp {

  private static final String PLUGIN_NAME = "PcscPlugin";

  public static void main(String[] args) {
    Console console = new Console();
    ValidationContext ctx = new ValidationContext(console);

    console.header("Keyple PC/SC Plugin Validation Tool  v1.0");
    console.println("Platform : " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
    console.println("Java     : " + System.getProperty("java.version"));

    // Initialise plugin
    if (!initialise(ctx)) {
      console.fail("Cannot continue without a working PC/SC environment.");
      return;
    }

    // Build module list
    List<AbstractModule> modules = buildModules(console);

    // Main menu loop
    mainLoop(modules, ctx, console);

    // Final report
    printReport(ctx, console);

    // Unregister plugin cleanly
    try {
      ctx.getService().unregisterPlugin(PLUGIN_NAME);
    } catch (Exception ignored) {
    }
  }

  // ── Initialisation ───────────────────────────────────────────────────────────

  private static boolean initialise(ValidationContext ctx) {
    Console console = ctx.getConsole();
    console.section("Initialising PC/SC plugin");
    try {
      SmartCardService service = SmartCardServiceProvider.getService();
      // Unregister any leftover instance from a previous run
      if (service.getPlugin(PLUGIN_NAME) != null) {
        service.unregisterPlugin(PLUGIN_NAME);
      }
      Plugin plugin = service.registerPlugin(PcscPluginFactoryBuilder.builder().build());
      ctx.setService(service);
      ctx.setPlugin(plugin);
      console.pass("Plugin registered: " + plugin.getName());

      if (plugin.getReaderNames().isEmpty()) {
        console.warn("No PC/SC readers detected. Some scenarios will be skipped.");
      } else {
        console.println("Readers found (" + plugin.getReaderNames().size() + "):");
        for (String name : plugin.getReaderNames()) {
          boolean contactless = plugin.getReader(name).isContactless();
          console.info("- " + name + "  " + (contactless ? "[contactless]" : "[contact]"));
        }
      }
      return true;
    } catch (Exception e) {
      console.fail("Plugin initialisation failed: " + e.getMessage());
      return false;
    }
  }

  // ── Module list ──────────────────────────────────────────────────────────────

  private static List<AbstractModule> buildModules(Console console) {
    return Arrays.asList(
        new M01_PluginDiscovery(console),
        new M02_ReaderConfiguration(console),
        new M03_PhysicalChannel(console),
        new M04_CardTypes(console),
        new M05_ProtocolIdentification(console),
        new M06_ApduExchange(console),
        new M07_ObservablePlugin(console),
        new M08_ObservableReader(console),
        new M09_PresenceMonitoring(console),
        new M10_Deselection(console),
        new M11_SpecialBehaviors(console),
        new M12_ControlCommands(console));
  }

  // ── Main menu ────────────────────────────────────────────────────────────────

  private static void mainLoop(List<AbstractModule> modules, ValidationContext ctx, Console console) {
    while (true) {
      console.header("Main Menu");
      List<String> options = new java.util.ArrayList<>();
      for (AbstractModule m : modules) {
        int total = m.getScenarios().size();
        options.add(m.getId() + " - " + m.getTitle() + "  [" + total + " scenarios]");
      }
      options.add("Run ALL modules");
      options.add("Show report");
      options.add("Quit");

      int choice = console.selectIndex("Select a module:", options);

      if (choice < modules.size()) {
        modules.get(choice).runInteractive(ctx);
      } else if (choice == modules.size()) {
        for (AbstractModule m : modules) {
          m.runInteractive(ctx);
        }
      } else if (choice == modules.size() + 1) {
        printReport(ctx, console);
      } else {
        return;
      }
    }
  }

  // ── Report ───────────────────────────────────────────────────────────────────

  private static void printReport(ValidationContext ctx, Console console) {
    console.header("Validation Report");
    List<ScenarioResult> results = ctx.getResults();
    if (results.isEmpty()) {
      console.println("No scenarios were executed.");
      return;
    }
    for (ScenarioResult r : results) {
      console.result(r);
    }
    console.section("Summary");
    long pass = 0, fail = 0, skip = 0;
    for (ScenarioResult r : results) {
      switch (r.getStatus()) {
        case PASS: pass++; break;
        case FAIL: fail++; break;
        case SKIP: skip++; break;
      }
    }
    console.summary(pass, fail, skip);
  }
}
