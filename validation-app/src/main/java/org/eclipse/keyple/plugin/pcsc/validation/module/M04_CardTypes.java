package org.eclipse.keyple.plugin.pcsc.validation.module;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.plugin.pcsc.PcscCardCommunicationProtocol;
import org.eclipse.keyple.plugin.pcsc.validation.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.validation.Console;
import org.eclipse.keyple.plugin.pcsc.validation.Scenario;
import org.eclipse.keyple.plugin.pcsc.validation.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.validation.ValidationContext;
import org.eclipse.keyple.plugin.pcsc.validation.util.AtrDecoder;
import org.eclipse.keypop.reader.CardReader;

/** M04 - Card type identification via ATR and protocol rules. */
public final class M04_CardTypes extends AbstractModule {

  public M04_CardTypes(Console console) {
    super("M04", "Card Types", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    for (PcscCardCommunicationProtocol proto : PcscCardCommunicationProtocol.values()) {
      final PcscCardCommunicationProtocol p = proto;
      list.add(
          new Scenario() {
            public String getId() { return "M04." + (p.ordinal() + 1); }
            public String getTitle() { return "Detect " + p.name() + " card"; }
            public String getRequiredEquipment() { return p.name() + " card"; }

            public ScenarioResult run(ValidationContext ctx) {
              CardReader reader = ctx.selectReader();
              if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
              ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
              ConfigurableReaderSpi configSpi = (ConfigurableReaderSpi) spi;
              ObservableReaderSpi obs = (ObservableReaderSpi) spi;
              ctx.getConsole().waitForEnter("Insert a " + p.name() + " card into " + reader.getName());
              try {
                boolean present = spi.isCardPresent();
                if (!present) {
                  return ScenarioResult.fail(getId(), "isCardPresent() returned false");
                }
                String atr = spi.getPowerOnData();
                console.info(AtrDecoder.describe(atr));
                boolean match = configSpi.isCurrentProtocol(p.name());
                console.info("isCurrentProtocol(" + p.name() + ") = " + match);
                obs.deselectCard();
                if (match) {
                  return ScenarioResult.pass(getId(), p.name() + " detected, ATR: " + atr);
                }
                return ScenarioResult.fail(
                    getId(), "Protocol not matched; ATR: " + atr);
              } catch (Exception e) {
                return ScenarioResult.fail(getId(), e.getMessage());
              }
            }
          });
    }

    // Extra: generic card presence check (useful as a sanity test)
    list.add(
        new Scenario() {
          public String getId() { return "M04.X"; }
          public String getTitle() { return "Unknown card type: read ATR and display decoded info"; }
          public String getRequiredEquipment() { return "Any card"; }

          public ScenarioResult run(ValidationContext ctx) {
            CardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert any card into " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              if (!present) {
                return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              }
              String atr = spi.getPowerOnData();
              for (String line : AtrDecoder.describe(atr).split("\n")) {
                console.info(line);
              }
              obs.deselectCard();
              return ScenarioResult.pass(getId(), "ATR read: " + atr);
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    return list;
  }
}
