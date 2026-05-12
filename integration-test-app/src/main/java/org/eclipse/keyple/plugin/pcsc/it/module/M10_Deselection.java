/* **************************************************************************************
 * Copyright (c) 2026 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.plugin.pcsc.it.module;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.plugin.pcsc.it.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.it.Console;
import org.eclipse.keyple.plugin.pcsc.it.Scenario;
import org.eclipse.keyple.plugin.pcsc.it.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.it.ValidationContext;
import org.eclipse.keypop.reader.ObservableCardReader;

/** M10 - Card deselection (S(DESELECT) / SCARD_UNPOWER_CARD). */
public final class M10_Deselection extends AbstractModule {

  public M10_Deselection(Console console) {
    super("M10", "Card Deselection", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() {
            return "M10.1";
          }

          public String getTitle() {
            return "deselectCard() puts contactless card in HALT; powerOnData preserved";
          }

          public String getRequiredEquipment() {
            return "ISO 14443-4 contactless card";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            if (!reader.isContactless()) {
              return ScenarioResult.skip(getId(), "Select a contactless reader");
            }
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Present an ISO 14443-4 card on " + reader.getName());
            try {
              obs.onStartDetection();
              boolean present = spi.isCardPresent();
              if (!present) {
                return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              }
              String atrBefore = spi.getPowerOnData();
              console.info("ATR before deselect: " + atrBefore);
              console.info("Channel open before deselectCard()");

              obs.deselectCard();
              console.info("deselectCard() called");
              String atrAfter = spi.getPowerOnData();
              console.info("ATR after deselect (preserved?): " + atrAfter);

              obs.onStopDetection();

              // After deselectCard(), powerOnData is preserved (card physically present in HALT)
              boolean atrPreserved = atrBefore.equals(atrAfter) && !atrAfter.isEmpty();

              if (atrPreserved) {
                return ScenarioResult.pass(
                    getId(), "ATR preserved after deselectCard(): " + atrAfter);
              }
              return ScenarioResult.fail(getId(), "atrPreserved=" + atrPreserved);
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M10.2";
          }

          public String getTitle() {
            return "Re-present card after deselectCard() -> new detection possible";
          }

          public String getRequiredEquipment() {
            return "ISO 14443-4 contactless card";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            if (!reader.isContactless()) {
              return ScenarioResult.skip(getId(), "Select a contactless reader");
            }
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Present a card on " + reader.getName());
            try {
              obs.onStartDetection();
              boolean p1 = spi.isCardPresent();
              if (!p1) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              String atr1 = spi.getPowerOnData();
              obs.deselectCard();
              console.info("Card deselected (HALT). Re-present the same card.");
              ctx.getConsole()
                  .waitForEnterOrTimeout("Re-present the card (or leave it on the reader)", 10_000);
              boolean p2 = spi.isCardPresent();
              String atr2 = spi.getPowerOnData();
              obs.deselectCard();
              obs.onStopDetection();
              console.info("ATR 1st: " + atr1);
              console.info("ATR 2nd: " + atr2);
              if (p2) {
                if (atr2.equalsIgnoreCase(atr1)) {
                  return ScenarioResult.pass(getId(), "Card re-detected after HALT; ATR matches");
                }
                return ScenarioResult.pass(
                    getId(), "Card re-detected; ATR1=" + atr1 + " ATR2=" + atr2);
              }
              return ScenarioResult.fail(getId(), "isCardPresent() returned false on re-present");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M10.3";
          }

          public String getTitle() {
            return "deselectCard() on closed channel is a no-op";
          }

          public String getRequiredEquipment() {
            return "";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            try {
              obs.onStartDetection();
              obs.deselectCard(); // must not throw when channel is closed
              obs.onStopDetection();
              console.info("deselectCard() on closed channel did not throw");
              return ScenarioResult.pass(getId(), "deselectCard() on closed channel is a no-op");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), "Unexpected exception: " + e.getMessage());
            }
          }
        });

    return list;
  }
}
