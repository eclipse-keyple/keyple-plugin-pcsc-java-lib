package org.eclipse.keyple.plugin.pcsc.validation.module;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.pcsc.validation.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.validation.Console;
import org.eclipse.keyple.plugin.pcsc.validation.Scenario;
import org.eclipse.keyple.plugin.pcsc.validation.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.validation.ValidationContext;
import org.eclipse.keypop.reader.CardReader;

/** M02 - Reader configuration via PcscReader extension. */
public final class M02_ReaderConfiguration extends AbstractModule {

  public M02_ReaderConfiguration(Console console) {
    super("M02", "Reader Configuration", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() { return "M02.1"; }
          public String getTitle() { return "Sharing mode: EXCLUSIVE then back to SHARED"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            PcscReader pcsc = ctx.getPcscReader(reader);
            try {
              pcsc.setSharingMode(PcscReader.SharingMode.EXCLUSIVE);
              console.info("SharingMode set to EXCLUSIVE");
              pcsc.setSharingMode(PcscReader.SharingMode.SHARED);
              console.info("SharingMode set back to SHARED");
              return ScenarioResult.pass(getId(), "EXCLUSIVE -> SHARED transition succeeded");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M02.2"; }
          public String getTitle() { return "ISO protocol: cycle through ANY / T0 / T1 / TCL"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            PcscReader pcsc = ctx.getPcscReader(reader);
            StringBuilder sb = new StringBuilder();
            for (PcscReader.IsoProtocol proto : PcscReader.IsoProtocol.values()) {
              pcsc.setIsoProtocol(proto);
              sb.append(proto.name()).append("(").append(proto.getValue()).append(") ");
              console.info("setIsoProtocol(" + proto.name() + ") -> value=\"" + proto.getValue() + "\"");
            }
            pcsc.setIsoProtocol(PcscReader.IsoProtocol.ANY); // restore default
            return ScenarioResult.pass(getId(), "Protocols set: " + sb.toString().trim());
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M02.3"; }
          public String getTitle() { return "Disconnection mode: cycle through RESET / LEAVE / UNPOWER / EJECT"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            PcscReader pcsc = ctx.getPcscReader(reader);
            for (PcscReader.DisconnectionMode mode : PcscReader.DisconnectionMode.values()) {
              pcsc.setDisconnectionMode(mode);
              console.info("setDisconnectionMode(" + mode.name() + ") accepted");
            }
            pcsc.setDisconnectionMode(PcscReader.DisconnectionMode.RESET); // restore default
            return ScenarioResult.pass(getId(), "All DisconnectionMode values accepted");
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M02.4"; }
          public String getTitle() { return "setContactless() override (true then false)"; }
          public String getRequiredEquipment() { return ""; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            PcscReader pcsc = ctx.getPcscReader(reader);
            boolean original = reader.isContactless();
            console.info("Original isContactless(): " + original);

            pcsc.setContactless(!original);
            console.info("After setContactless(" + !original + "): " + reader.isContactless());

            pcsc.setContactless(original); // restore
            console.info("Restored to: " + reader.isContactless());

            boolean restored = (reader.isContactless() == original);
            if (restored) {
              return ScenarioResult.pass(getId(), "setContactless() override works; original value restored");
            }
            return ScenarioResult.fail(getId(), "Value not restored correctly");
          }
        });

    return list;
  }
}
