package org.eclipse.keyple.plugin.pcsc.validation.module;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.CardRemovalWaiterBlockingSpi;
import org.eclipse.keyple.plugin.pcsc.PcscCardCommunicationProtocol;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.pcsc.validation.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.validation.Console;
import org.eclipse.keyple.plugin.pcsc.validation.Scenario;
import org.eclipse.keyple.plugin.pcsc.validation.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.validation.ValidationContext;
import org.eclipse.keypop.reader.CardReader;

/** M11 - Special plugin behaviours (Innovatron, anti-collision, isCardPresent). */
public final class M11_SpecialBehaviors extends AbstractModule {

  public M11_SpecialBehaviors(Console console) {
    super("M11", "Special Behaviors", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() { return "M11.1"; }
          public String getTitle() { return "Innovatron B Prime: removal detected by polling (25 ms)"; }
          public String getRequiredEquipment() { return "Calypso / Innovatron B Prime card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            if (!reader.isContactless()) {
              return ScenarioResult.skip(getId(), "Select a contactless reader");
            }
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ConfigurableReaderSpi cfg = (ConfigurableReaderSpi) spi;
            CardRemovalWaiterBlockingSpi removalSpi = (CardRemovalWaiterBlockingSpi) spi;

            ctx.getConsole().waitForEnter("Insert an Innovatron B Prime (Calypso) card");
            try {
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              String atr = spi.getPowerOnData();
              console.info("ATR: " + atr);
              boolean isInnovatron = cfg.isCurrentProtocol(PcscCardCommunicationProtocol.INNOVATRON_B_PRIME.name());
              console.info("Protocol INNOVATRON_B_PRIME: " + isInnovatron);
              if (!isInnovatron) {
                ((ObservableReaderSpi) spi).deselectCard();
                return ScenarioResult.skip(getId(), "Card is not Innovatron B Prime; ATR=" + atr);
              }

              CountDownLatch latch = new CountDownLatch(1);
              final long[] elapsed = {0};
              long start = System.currentTimeMillis();

              Thread t = new Thread(() -> {
                try {
                  removalSpi.waitForCardRemoval();
                  elapsed[0] = System.currentTimeMillis() - start;
                } catch (Exception e) {
                  elapsed[0] = -1;
                } finally {
                  latch.countDown();
                }
              });
              t.setDaemon(true);
              t.start();

              ctx.getConsole().waitForEnterOrTimeout(
                  "Remove the Innovatron B Prime card", 30_000);
              boolean done = latch.await(3, TimeUnit.SECONDS);

              if (done && elapsed[0] >= 0) {
                console.info("Removal detected in " + elapsed[0] + " ms (polling @ 25 ms)");
                return ScenarioResult.pass(getId(), "Innovatron removal detected in " + elapsed[0] + " ms");
              }
              removalSpi.stopWaitForCardRemoval();
              return ScenarioResult.fail(getId(), "Removal not detected");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M11.2"; }
          public String getTitle() { return "isCardPresent() anti-collision: connects when channel is closed"; }
          public String getRequiredEquipment() { return "Any contactless card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            if (!reader.isContactless()) {
              return ScenarioResult.skip(getId(), "Select a contactless reader");
            }
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Present a card on " + reader.getName());
            try {
              // Ensure channel is closed first (start from clean state)
              obs.deselectCard();
              console.info("Channel closed before isCardPresent()");
              boolean present = spi.isCardPresent();
              console.info("isCardPresent() = " + present);
              String atr = spi.getPowerOnData();
              console.info("ATR: " + atr);

              if (present && !atr.isEmpty()) {
                obs.deselectCard();
                return ScenarioResult.pass(getId(),
                    "Anti-collision: channel opened by isCardPresent(); ATR=" + atr);
              }
              return ScenarioResult.fail(getId(),
                  "present=" + present + " atr=" + atr);
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M11.3"; }
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
              ((ObservableReaderSpi) spi).deselectCard();
              return ScenarioResult.fail(getId(), "Expected false but got true (is a card present?)");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() { return "M11.4"; }
          public String getTitle() { return "SCARD_LEAVE_CARD: isCardPresent() reconnects after deselectCard()"; }
          public String getRequiredEquipment() { return "Any card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            PcscReader pcsc = ctx.getPcscReader(reader);
            ReaderSpi spi = (ReaderSpi) pcsc;
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              pcsc.setDisconnectionMode(PcscReader.DisconnectionMode.LEAVE);
              boolean p1 = spi.isCardPresent();
              if (!p1) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              String atr1 = spi.getPowerOnData();
              console.info("ATR (1st connect): " + atr1);
              obs.deselectCard();
              console.info("deselectCard() called");

              // With LEAVE mode as disconnectionMode context, reconnect via isCardPresent()
              boolean p2 = spi.isCardPresent();
              String atr2 = spi.getPowerOnData();
              console.info("ATR (2nd connect): " + atr2);
              obs.deselectCard();
              pcsc.setDisconnectionMode(PcscReader.DisconnectionMode.RESET);

              if (p2) {
                return ScenarioResult.pass(getId(),
                    "Reconnect succeeded; ATR=" + atr2);
              }
              return ScenarioResult.fail(getId(), "isCardPresent() returned false on reconnect");
            } catch (Exception e) {
              pcsc.setDisconnectionMode(PcscReader.DisconnectionMode.RESET);
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    return list;
  }
}
