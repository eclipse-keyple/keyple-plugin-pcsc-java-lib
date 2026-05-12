package org.eclipse.keyple.plugin.pcsc.validation.module;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.pcsc.validation.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.validation.Console;
import org.eclipse.keyple.plugin.pcsc.validation.Scenario;
import org.eclipse.keyple.plugin.pcsc.validation.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.validation.ValidationContext;
import org.eclipse.keypop.reader.CardReader;

/**
 * M03 - Physical channel management (API 3.0.0).
 *
 * <p>These scenarios access the SPI layer directly by casting the reader to {@link ReaderSpi},
 * bypassing the service layer to observe raw channel behaviour.
 *
 * <p>In API 3.0.0, {@code openPhysicalChannel()}, {@code closePhysicalChannel()} and {@code
 * isPhysicalChannelOpen()} have been removed from {@link ReaderSpi}. Channel management is now
 * performed via {@code isCardPresent()} (connect + detect) and {@code deselectCard()} (deselect).
 */
public final class M03_PhysicalChannel extends AbstractModule {

  public M03_PhysicalChannel(Console console) {
    super("M03", "Physical Channel Management", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() { return "M03.1"; }
          public String getTitle() { return "isCardPresent() with card present: connects and returns ATR"; }
          public String getRequiredEquipment() { return "Any card in the reader"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              console.info("isCardPresent() = " + present);
              if (!present) {
                return ScenarioResult.fail(getId(), "isCardPresent() returned false but card was inserted");
              }
              String atr = spi.getPowerOnData();
              console.info("ATR: " + atr);
              obs.deselectCard();
              console.info("deselectCard() called; ATR preserved: " + spi.getPowerOnData());
              if (!atr.isEmpty()) {
                return ScenarioResult.pass(getId(), "isCardPresent() connected + ATR: " + atr);
              }
              return ScenarioResult.fail(getId(), "ATR was empty after isCardPresent()");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M03.2"; }
          public String getTitle() { return "isCardPresent() is idempotent when card stays present"; }
          public String getRequiredEquipment() { return "Any card in the reader"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              boolean p1 = spi.isCardPresent();
              String atr1 = spi.getPowerOnData();
              boolean p2 = spi.isCardPresent(); // second call — channel already open
              String atr2 = spi.getPowerOnData();
              obs.deselectCard();
              if (p1 && p2 && atr1.equals(atr2)) {
                return ScenarioResult.pass(getId(), "Second isCardPresent() was no-op; ATR unchanged: " + atr1);
              }
              return ScenarioResult.fail(getId(),
                  "p1=" + p1 + " p2=" + p2 + " atr1=" + atr1 + " atr2=" + atr2);
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M03.3"; }
          public String getTitle() { return "isCardPresent() returns false when no card"; }
          public String getRequiredEquipment() { return "Empty reader (no card)"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ctx.getConsole().waitForEnter("Ensure NO card is in " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              console.info("isCardPresent() = " + present);
              if (!present) {
                return ScenarioResult.pass(getId(), "No card: isCardPresent() = false");
              }
              return ScenarioResult.fail(getId(), "Expected false but got true (is a card present?)");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M03.4"; }
          public String getTitle() { return "After deselectCard(), isCardPresent() reconnects"; }
          public String getRequiredEquipment() { return "Any card in the reader"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              boolean p1 = spi.isCardPresent();
              String atr1 = spi.getPowerOnData();
              console.info("ATR before deselect: " + atr1);
              obs.deselectCard();
              console.info("deselectCard() called");
              boolean p2 = spi.isCardPresent();
              String atr2 = spi.getPowerOnData();
              console.info("ATR after reconnect: " + atr2);
              obs.deselectCard();
              if (p1 && p2) {
                return ScenarioResult.pass(getId(), "Reconnected after deselectCard(); ATR: " + atr2);
              }
              return ScenarioResult.fail(getId(), "p1=" + p1 + " p2=" + p2);
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M03.5"; }
          public String getTitle() { return "isCardPresent() with each DisconnectionMode"; }
          public String getRequiredEquipment() { return "Any card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            PcscReader pcsc = ctx.getPcscReader(reader);
            ReaderSpi spi = (ReaderSpi) pcsc;
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            StringBuilder result = new StringBuilder();
            for (PcscReader.DisconnectionMode mode : PcscReader.DisconnectionMode.values()) {
              ctx.getConsole().waitForEnter("Insert a card for mode " + mode.name());
              try {
                pcsc.setDisconnectionMode(mode);
                boolean present = spi.isCardPresent();
                String atr = spi.getPowerOnData();
                obs.deselectCard();
                result.append(mode.name()).append(":").append(present ? "OK" : "FAIL").append(" ");
                console.info(mode.name() + " -> " + (present ? "OK ATR=" + atr : "FAIL"));
              } catch (Exception e) {
                result.append(mode.name()).append(":FAIL(").append(e.getMessage()).append(") ");
                console.warn(mode.name() + " -> " + e.getMessage());
              }
            }
            pcsc.setDisconnectionMode(PcscReader.DisconnectionMode.RESET);
            return ScenarioResult.pass(getId(), result.toString().trim());
          }
        });

    return list;
  }
}
