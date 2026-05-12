package org.eclipse.keyple.plugin.pcsc.validation.module;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.core.util.HexUtil;
import org.eclipse.keyple.plugin.pcsc.validation.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.validation.Console;
import org.eclipse.keyple.plugin.pcsc.validation.Scenario;
import org.eclipse.keyple.plugin.pcsc.validation.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.validation.ValidationContext;
import org.eclipse.keyple.plugin.pcsc.validation.util.ApduBuilder;
import org.eclipse.keypop.reader.CardReader;

/** M06 - APDU exchange (transmitApdu). */
public final class M06_ApduExchange extends AbstractModule {

  public M06_ApduExchange(Console console) {
    super("M06", "APDU Exchange", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() { return "M06.1"; }
          public String getTitle() { return "GET CHALLENGE (works on most ISO cards)"; }
          public String getRequiredEquipment() { return "Any ISO 7816 card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert an ISO card into " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              byte[] cmd = ApduBuilder.getChallenge();
              console.info("CMD: " + HexUtil.toHex(cmd));
              byte[] resp = spi.transmitApdu(cmd);
              console.info("RSP: " + HexUtil.toHex(resp) + "  SW=" + ApduBuilder.swHex(resp));
              obs.deselectCard();
              if (ApduBuilder.isOk(resp)) {
                return ScenarioResult.pass(getId(), "GET CHALLENGE -> SW=" + ApduBuilder.swHex(resp));
              }
              return ScenarioResult.fail(getId(), "Unexpected SW: " + ApduBuilder.swHex(resp));
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M06.2"; }
          public String getTitle() { return "SELECT Master File (00 A4 00 00)"; }
          public String getRequiredEquipment() { return "Any ISO 7816 card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert an ISO card into " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              byte[] cmd = ApduBuilder.selectMasterFile();
              console.info("CMD: " + HexUtil.toHex(cmd));
              byte[] resp = spi.transmitApdu(cmd);
              console.info("RSP: " + HexUtil.toHex(resp) + "  SW=" + ApduBuilder.swHex(resp));
              obs.deselectCard();
              return ScenarioResult.pass(getId(), "SELECT MF -> SW=" + ApduBuilder.swHex(resp));
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M06.3"; }
          public String getTitle() { return "User-defined APDU (enter hex command)"; }
          public String getRequiredEquipment() { return "Any card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            String hex = ctx.getConsole().prompt("Enter APDU command bytes (hex, no spaces)");
            if (hex.isEmpty()) return ScenarioResult.skip(getId(), "No command entered");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              byte[] cmd = HexUtil.toByteArray(hex);
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              console.info("CMD: " + HexUtil.toHex(cmd));
              byte[] resp = spi.transmitApdu(cmd);
              console.info("RSP: " + HexUtil.toHex(resp) + "  SW=" + ApduBuilder.swHex(resp));
              obs.deselectCard();
              return ScenarioResult.pass(getId(), "SW=" + ApduBuilder.swHex(resp));
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M06.4"; }
          public String getTitle() { return "Card removal during APDU -> CardIOException"; }
          public String getRequiredEquipment() { return "Any card (will be removed during test)"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              console.info("Channel open. Remove the card NOW and press ENTER.");
              ctx.getConsole().waitForEnter("(card removed, press ENTER to send APDU)");
              byte[] resp = spi.transmitApdu(ApduBuilder.getChallenge());
              // If we reach here, no exception was thrown - unexpected
              return ScenarioResult.fail(
                  getId(), "Expected CardIOException but got response: " + HexUtil.toHex(resp));
            } catch (org.eclipse.keyple.core.plugin.CardIOException e) {
              console.info("CardIOException received as expected: " + e.getMessage());
              return ScenarioResult.pass(getId(), "CardIOException thrown on removed card");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), "Wrong exception type: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M06.5"; }
          public String getTitle() { return "Sequential APDUs: 5x GET CHALLENGE"; }
          public String getRequiredEquipment() { return "Any ISO 7816 card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              int ok = 0;
              for (int i = 1; i <= 5; i++) {
                byte[] resp = spi.transmitApdu(ApduBuilder.getChallenge());
                console.info("APDU " + i + " SW=" + ApduBuilder.swHex(resp));
                if (ApduBuilder.isOk(resp)) ok++;
              }
              obs.deselectCard();
              if (ok == 5) {
                return ScenarioResult.pass(getId(), "5/5 sequential APDUs successful");
              }
              return ScenarioResult.fail(getId(), "Only " + ok + "/5 APDUs succeeded");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    return list;
  }
}
