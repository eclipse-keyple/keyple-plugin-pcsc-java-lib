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
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.plugin.pcsc.PcscCardCommunicationProtocol;
import org.eclipse.keyple.plugin.pcsc.PcscPluginFactoryBuilder;
import org.eclipse.keyple.plugin.pcsc.it.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.it.Console;
import org.eclipse.keyple.plugin.pcsc.it.Scenario;
import org.eclipse.keyple.plugin.pcsc.it.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.it.ValidationContext;
import org.eclipse.keypop.reader.CardReader;
import org.eclipse.keypop.reader.ObservableCardReader;

/** M05 - Protocol identification rules (ATR regex). */
public final class M05_ProtocolIdentification extends AbstractModule {

  public M05_ProtocolIdentification(Console console) {
    super("M05", "Protocol Identification", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() {
            return "M05.1";
          }

          public String getTitle() {
            return "Read ATR and test all protocol rules against it";
          }

          public String getRequiredEquipment() {
            return "Any card";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ConfigurableReaderSpi cfg = (ConfigurableReaderSpi) spi;
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              if (!present) {
                return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              }
              String atr = spi.getPowerOnData();
              console.info("ATR: " + atr);
              StringBuilder matches = new StringBuilder();
              for (PcscCardCommunicationProtocol p : PcscCardCommunicationProtocol.values()) {
                boolean match = cfg.isCurrentProtocol(p.name());
                console.info(
                    String.format("  %-25s -> %s", p.name(), match ? "MATCH" : "no match"));
                if (match) matches.append(p.name()).append(" ");
              }
              obs.deselectCard();
              String result = matches.length() > 0 ? matches.toString().trim() : "no match";
              return ScenarioResult.pass(getId(), "Matched: " + result);
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M05.2";
          }

          public String getTitle() {
            return "isProtocolSupported() for all known protocols";
          }

          public String getRequiredEquipment() {
            return "";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ConfigurableReaderSpi cfg = (ConfigurableReaderSpi) ctx.getPcscReader(reader);
            int supported = 0;
            for (PcscCardCommunicationProtocol p : PcscCardCommunicationProtocol.values()) {
              boolean ok = cfg.isProtocolSupported(p.name());
              console.info(
                  String.format("  %-25s -> %s", p.name(), ok ? "supported" : "NOT supported"));
              if (ok) supported++;
            }
            if (supported == PcscCardCommunicationProtocol.values().length) {
              return ScenarioResult.pass(getId(), "All " + supported + " protocols supported");
            }
            return ScenarioResult.fail(
                getId(),
                "Only "
                    + supported
                    + "/"
                    + PcscCardCommunicationProtocol.values().length
                    + " protocols reported as supported");
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M05.3";
          }

          public String getTitle() {
            return "Custom protocol rule: user-supplied ATR regex";
          }

          public String getRequiredEquipment() {
            return "A card matching the custom rule";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            String customName = ctx.getConsole().prompt("Custom protocol name");
            String customRegex = ctx.getConsole().prompt("ATR regex for this protocol");
            if (customName.isEmpty() || customRegex.isEmpty()) {
              return ScenarioResult.skip(getId(), "No name/regex entered");
            }
            try {
              ctx.getService().unregisterPlugin("PcscPlugin");
              ctx.setPlugin(
                  ctx.getService()
                      .registerPlugin(
                          PcscPluginFactoryBuilder.builder()
                              .updateProtocolIdentificationRule(customName, customRegex)
                              .build()));
              console.info(
                  "Plugin re-registered with custom rule: " + customName + " = " + customRegex);

              ReaderSpi spi =
                  (ReaderSpi) ctx.getPcscReader(ctx.getPlugin().getReader(reader.getName()));
              ConfigurableReaderSpi cfg = (ConfigurableReaderSpi) spi;
              ObservableReaderSpi obs = (ObservableReaderSpi) spi;
              ctx.getConsole()
                  .waitForEnter("Insert a card that should match \"" + customName + "\"");
              boolean present = spi.isCardPresent();
              if (!present) {
                return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              }
              console.info("ATR: " + spi.getPowerOnData());
              boolean match = cfg.isCurrentProtocol(customName);
              obs.deselectCard();
              if (match) {
                return ScenarioResult.pass(getId(), "Custom rule \"" + customName + "\" matched");
              }
              return ScenarioResult.fail(
                  getId(), "Custom rule did not match; ATR=" + spi.getPowerOnData());
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M05.4";
          }

          public String getTitle() {
            return "Disable a protocol rule (set to null)";
          }

          public String getRequiredEquipment() {
            return "A card matching ISO_14443_4";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            try {
              // Disable ISO_14443_4
              ctx.getService().unregisterPlugin("PcscPlugin");
              ctx.setPlugin(
                  ctx.getService()
                      .registerPlugin(
                          PcscPluginFactoryBuilder.builder()
                              .updateProtocolIdentificationRule(
                                  PcscCardCommunicationProtocol.ISO_14443_4.name(), null)
                              .build()));
              console.info("ISO_14443_4 rule disabled");

              CardReader newReader = ctx.getPlugin().getReader(reader.getName());
              if (newReader == null) {
                return ScenarioResult.skip(getId(), "Reader not found after re-registration");
              }
              ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(newReader);
              ConfigurableReaderSpi cfg = (ConfigurableReaderSpi) spi;
              ObservableReaderSpi obs = (ObservableReaderSpi) spi;
              ctx.getConsole()
                  .waitForEnter("Insert an ISO 14443-4 card to verify rule is disabled");
              boolean present = spi.isCardPresent();
              if (!present) {
                return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              }
              console.info("ATR: " + spi.getPowerOnData());
              boolean match =
                  cfg.isCurrentProtocol(PcscCardCommunicationProtocol.ISO_14443_4.name());
              obs.deselectCard();

              // Restore
              ctx.getService().unregisterPlugin("PcscPlugin");
              ctx.setPlugin(
                  ctx.getService().registerPlugin(PcscPluginFactoryBuilder.builder().build()));

              if (!match) {
                return ScenarioResult.pass(getId(), "Disabled rule correctly returns no match");
              }
              return ScenarioResult.fail(getId(), "Disabled rule still matches");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    return list;
  }
}
