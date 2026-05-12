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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.processing.CardPresenceMonitorBlockingSpi;
import org.eclipse.keyple.plugin.pcsc.it.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.it.Console;
import org.eclipse.keyple.plugin.pcsc.it.Scenario;
import org.eclipse.keyple.plugin.pcsc.it.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.it.ValidationContext;
import org.eclipse.keypop.reader.ObservableCardReader;

/** M09 - Card presence monitoring during processing. */
public final class M09_PresenceMonitoring extends AbstractModule {

  public M09_PresenceMonitoring(Console console) {
    super("M09", "Presence Monitoring During Processing", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() {
            return "M09.1";
          }

          public String getTitle() {
            return "monitorCardPresenceDuringProcessing() returns normally when card stays";
          }

          public String getRequiredEquipment() {
            return "Any card (keep it in the reader)";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            CardPresenceMonitorBlockingSpi monitor = (CardPresenceMonitorBlockingSpi) spi;
            ctx.getConsole()
                .waitForEnter("Insert a card into " + reader.getName() + " and keep it there");
            try {
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              console.info("Channel open. Starting presence monitor...");

              CountDownLatch monitorFinished = new CountDownLatch(1);
              final Exception[] monitorException = {null};

              Thread monitorThread =
                  new Thread(
                      () -> {
                        try {
                          monitor.monitorCardPresenceDuringProcessing();
                        } catch (Exception e) {
                          monitorException[0] = e;
                        } finally {
                          monitorFinished.countDown();
                        }
                      });
              monitorThread.setDaemon(true);
              monitorThread.start();

              // Let monitoring run for 2 s then stop it
              Thread.sleep(2000);
              monitor.stopCardPresenceMonitoringDuringProcessing();
              boolean finished = monitorFinished.await(3, TimeUnit.SECONDS);
              obs.deselectCard();

              if (!finished) {
                return ScenarioResult.fail(getId(), "Monitor thread did not finish after stop()");
              }
              if (monitorException[0]
                  instanceof org.eclipse.keyple.core.plugin.TaskCanceledException) {
                return ScenarioResult.pass(
                    getId(), "Monitor stopped cleanly via TaskCanceledException");
              }
              if (monitorException[0] != null) {
                return ScenarioResult.fail(
                    getId(), "Unexpected exception: " + monitorException[0].getMessage());
              }
              return ScenarioResult.pass(getId(), "Monitor returned normally after stop()");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M09.2";
          }

          public String getTitle() {
            return "Card removal detected during monitorCardPresenceDuringProcessing()";
          }

          public String getRequiredEquipment() {
            return "Any card (will be removed during test)";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            CardPresenceMonitorBlockingSpi monitor = (CardPresenceMonitorBlockingSpi) spi;
            ctx.getConsole().waitForEnter("Insert a card into " + reader.getName());
            try {
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");
              console.info("Channel open. Starting presence monitor...");

              CountDownLatch monitorFinished = new CountDownLatch(1);
              final Exception[] monitorException = {null};

              Thread monitorThread =
                  new Thread(
                      () -> {
                        try {
                          monitor.monitorCardPresenceDuringProcessing();
                        } catch (Exception e) {
                          monitorException[0] = e;
                        } finally {
                          monitorFinished.countDown();
                        }
                      });
              monitorThread.setDaemon(true);
              monitorThread.start();

              ctx.getConsole()
                  .waitForEnterOrTimeout(
                      "REMOVE the card from " + reader.getName() + " now", 30_000);
              boolean finished = monitorFinished.await(5, TimeUnit.SECONDS);

              if (!finished) {
                monitor.stopCardPresenceMonitoringDuringProcessing();
                return ScenarioResult.fail(getId(), "Monitor did not detect card removal");
              }
              if (monitorException[0] != null) {
                console.info("Exception type: " + monitorException[0].getClass().getSimpleName());
                console.info("Message: " + monitorException[0].getMessage());
                return ScenarioResult.pass(
                    getId(),
                    "Card removal detected: " + monitorException[0].getClass().getSimpleName());
              }
              return ScenarioResult.pass(
                  getId(),
                  "monitorCardPresenceDuringProcessing() returned normally on card removal");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M09.3";
          }

          public String getTitle() {
            return "stopCardPresenceMonitoringDuringProcessing() cancels the wait";
          }

          public String getRequiredEquipment() {
            return "Any card (keep it in the reader)";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            ReaderSpi spi = (ReaderSpi) ctx.getPcscReader(reader);
            ObservableReaderSpi obs = (ObservableReaderSpi) spi;
            CardPresenceMonitorBlockingSpi monitor = (CardPresenceMonitorBlockingSpi) spi;
            ctx.getConsole()
                .waitForEnter("Insert a card into " + reader.getName() + " and keep it");
            try {
              boolean present = spi.isCardPresent();
              if (!present) return ScenarioResult.fail(getId(), "isCardPresent() returned false");

              CountDownLatch latch = new CountDownLatch(1);
              final boolean[] cancelled = {false};

              Thread t =
                  new Thread(
                      () -> {
                        try {
                          monitor.monitorCardPresenceDuringProcessing();
                        } catch (org.eclipse.keyple.core.plugin.TaskCanceledException e) {
                          cancelled[0] = true;
                        } catch (Exception ignored) {
                        } finally {
                          latch.countDown();
                        }
                      });
              t.setDaemon(true);
              t.start();

              Thread.sleep(500);
              long stopAt = System.currentTimeMillis();
              monitor.stopCardPresenceMonitoringDuringProcessing();
              boolean done = latch.await(3, TimeUnit.SECONDS);
              long elapsed = System.currentTimeMillis() - stopAt;
              obs.deselectCard();

              if (done) {
                console.info(
                    "Cancellation elapsed: "
                        + elapsed
                        + " ms; TaskCanceledException="
                        + cancelled[0]);
                return ScenarioResult.pass(getId(), "Monitoring cancelled in " + elapsed + " ms");
              }
              return ScenarioResult.fail(getId(), "Monitor did not stop after cancel within 3 s");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    return list;
  }
}
