package org.eclipse.keyple.plugin.pcsc.validation.module;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.core.service.Plugin;
import org.eclipse.keyple.plugin.pcsc.PcscPlugin;
import org.eclipse.keyple.plugin.pcsc.PcscPluginFactoryBuilder;
import org.eclipse.keyple.plugin.pcsc.validation.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.validation.Console;
import org.eclipse.keyple.plugin.pcsc.validation.Scenario;
import org.eclipse.keyple.plugin.pcsc.validation.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.validation.ValidationContext;
import org.eclipse.keypop.reader.CardReader;

/** M01 - Plugin discovery and basic properties. */
public final class M01_PluginDiscovery extends AbstractModule {

  public M01_PluginDiscovery(Console console) {
    super("M01", "Plugin Discovery", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() { return "M01.1"; }
          public String getTitle() { return "Plugin name equals 'PcscPlugin'"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            String name = ctx.getPlugin().getName();
            console.info("Plugin name: " + name);
            if ("PcscPlugin".equals(name)) {
              return ScenarioResult.pass(getId(), "getName() = \"PcscPlugin\"");
            }
            return ScenarioResult.fail(getId(), "Unexpected name: \"" + name + "\"");
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M01.2"; }
          public String getTitle() { return "List all connected PC/SC readers"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            Plugin plugin = ctx.getPlugin();
            List<String> names = new ArrayList<>(plugin.getReaderNames());
            if (names.isEmpty()) {
              return ScenarioResult.skip(getId(), "No readers connected");
            }
            for (String n : names) {
              boolean cl = plugin.getReader(n).isContactless();
              console.info("- " + n + "  " + (cl ? "[contactless]" : "[contact]"));
            }
            return ScenarioResult.pass(getId(), names.size() + " reader(s) found");
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M01.3"; }
          public String getTitle() { return "Auto-detect contact vs contactless by reader name"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            Plugin plugin = ctx.getPlugin();
            if (plugin.getReaderNames().isEmpty()) {
              return ScenarioResult.skip(getId(), "No readers connected");
            }
            int contactless = 0, contact = 0;
            for (CardReader r : plugin.getReaders()) {
              if (r.isContactless()) contactless++; else contact++;
              console.info(r.getName() + " -> " + (r.isContactless() ? "contactless" : "contact"));
            }
            return ScenarioResult.pass(
                getId(), "contact=" + contact + " contactless=" + contactless);
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M01.4"; }
          public String getTitle() { return "Custom contactless identification filter (user regex)"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            if (ctx.getPlugin().getReaderNames().isEmpty()) {
              return ScenarioResult.skip(getId(), "No readers connected");
            }
            String regex = ctx.getConsole().prompt(
                "Enter contactless identification regex (current default: '(?i).*(contactless|ask logo|acs acr122).*')");
            if (regex.isEmpty()) {
              return ScenarioResult.skip(getId(), "No regex entered");
            }
            try {
              // Re-register the plugin with the custom filter
              ctx.getService().unregisterPlugin("PcscPlugin");
              Plugin plugin = ctx.getService().registerPlugin(
                  PcscPluginFactoryBuilder.builder()
                      .useContactlessReaderIdentificationFilter(regex)
                      .build());
              ctx.setPlugin(plugin);
              console.info("Plugin re-registered with custom filter: " + regex);
              for (CardReader r : plugin.getReaders()) {
                console.info(r.getName() + " -> " + (r.isContactless() ? "contactless" : "contact"));
              }
              return ScenarioResult.pass(getId(), "Custom filter applied: \"" + regex + "\"");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M01.5"; }
          public String getTitle() { return "Verify PcscPlugin extension is accessible"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            try {
              PcscPlugin ext = ctx.getPlugin().getExtension(PcscPlugin.class);
              if (ext != null) {
                return ScenarioResult.pass(getId(), "getExtension(PcscPlugin.class) returned non-null");
              }
              return ScenarioResult.fail(getId(), "getExtension returned null");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    return list;
  }
}
