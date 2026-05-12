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
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.keyple.plugin.pcsc.PcscPluginFactoryBuilder;
import org.eclipse.keyple.plugin.pcsc.it.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.it.Console;
import org.eclipse.keyple.plugin.pcsc.it.Scenario;
import org.eclipse.keyple.plugin.pcsc.it.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.it.ValidationContext;
import org.eclipse.keypop.reader.CardReaderEvent;
import org.eclipse.keypop.reader.ObservableCardReader;
import org.eclipse.keypop.reader.spi.CardReaderObserverSpi;

/** M08 - Observable reader: card insertion / removal events. */
public final class M08_ObservableReader extends AbstractModule {

  private static final long EVENT_TIMEOUT_MS = 30_000;

  public M08_ObservableReader(Console console) {
    super("M08", "Observable Reader (Card Events)", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() {
            return "M08.1";
          }

          public String getTitle() {
            return "CARD_INSERTED event on card presentation";
          }

          public String getRequiredEquipment() {
            return "Any card";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            if (!(reader instanceof ObservableCardReader)) {
              return ScenarioResult.skip(getId(), "Reader is not observable");
            }
            ObservableCardReader obs = (ObservableCardReader) reader;
            CountDownLatch latch = new CountDownLatch(1);
            final String[] atr = {null};

            CardReaderObserverSpi observer =
                event -> {
                  if (event.getType() == CardReaderEvent.Type.CARD_INSERTED) {
                    atr[0] =
                        ctx.getPlugin()
                            .getReaderExtension(
                                org.eclipse.keyple.plugin.pcsc.PcscReader.class, reader.getName())
                            .toString();
                    latch.countDown();
                    reader.finalizeCardProcessing();
                  }
                };
            obs.setReaderObservationExceptionHandler(
                (info, name, e) -> {
                  console.warn("Reader obs error: " + e.getMessage());
                  e.printStackTrace();
                });
            obs.addObserver(observer);
            obs.startCardDetection(ObservableCardReader.DetectionMode.REPEATING);

            try {
              ctx.getConsole()
                  .waitForEnterOrTimeout("Present a card on " + reader.getName(), EVENT_TIMEOUT_MS);
              boolean received = latch.await(3, TimeUnit.SECONDS);
              obs.stopCardDetection();
              obs.removeObserver(observer);
              if (received) {
                return ScenarioResult.pass(
                    getId(), "CARD_INSERTED event received on " + reader.getName());
              }
              return ScenarioResult.fail(getId(), "No CARD_INSERTED event within timeout");
            } catch (Exception e) {
              obs.stopCardDetection();
              obs.removeObserver(observer);
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M08.2";
          }

          public String getTitle() {
            return "CARD_REMOVED event on card withdrawal";
          }

          public String getRequiredEquipment() {
            return "Any card";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            if (!(reader instanceof ObservableCardReader)) {
              return ScenarioResult.skip(getId(), "Reader is not observable");
            }
            ObservableCardReader obs = (ObservableCardReader) reader;
            CountDownLatch insertLatch = new CountDownLatch(1);
            CountDownLatch removeLatch = new CountDownLatch(1);

            CardReaderObserverSpi observer =
                event -> {
                  if (event.getType() == CardReaderEvent.Type.CARD_INSERTED) {
                    insertLatch.countDown();
                  } else if (event.getType() == CardReaderEvent.Type.CARD_REMOVED) {
                    removeLatch.countDown();
                  }
                };
            obs.setReaderObservationExceptionHandler(
                (info, name, e) -> {
                  console.warn("Reader obs error: " + e.getMessage());
                  e.printStackTrace();
                });
            obs.addObserver(observer);
            obs.startCardDetection(ObservableCardReader.DetectionMode.REPEATING);

            try {
              ctx.getConsole()
                  .waitForEnterOrTimeout("Present a card on " + reader.getName(), EVENT_TIMEOUT_MS);
              boolean inserted = insertLatch.await(3, TimeUnit.SECONDS);
              if (inserted) {
                console.info("CARD_INSERTED received.");
                reader.finalizeCardProcessing();
              }
              ctx.getConsole()
                  .waitForEnterOrTimeout(
                      "Now remove the card from " + reader.getName(), EVENT_TIMEOUT_MS);
              boolean removed = removeLatch.await(3, TimeUnit.SECONDS);
              obs.stopCardDetection();
              obs.removeObserver(observer);
              if (removed) {
                return ScenarioResult.pass(getId(), "CARD_REMOVED event received");
              }
              return ScenarioResult.fail(getId(), "No CARD_REMOVED event within timeout");
            } catch (Exception e) {
              obs.stopCardDetection();
              obs.removeObserver(observer);
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M08.3";
          }

          public String getTitle() {
            return "Multiple insert/remove cycles (3 times)";
          }

          public String getRequiredEquipment() {
            return "Any card";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            if (!(reader instanceof ObservableCardReader)) {
              return ScenarioResult.skip(getId(), "Reader is not observable");
            }
            ObservableCardReader obs = (ObservableCardReader) reader;
            int cycles = 3;
            AtomicInteger inserts = new AtomicInteger(0);
            AtomicInteger removals = new AtomicInteger(0);

            CardReaderObserverSpi observer =
                event -> {
                  if (event.getType() == CardReaderEvent.Type.CARD_INSERTED) {
                    console.info("  Cycle " + (inserts.incrementAndGet()) + ": CARD_INSERTED");
                    reader.finalizeCardProcessing();
                  } else if (event.getType() == CardReaderEvent.Type.CARD_REMOVED) {
                    console.info("  Cycle " + removals.incrementAndGet() + ": CARD_REMOVED");
                  }
                };
            obs.setReaderObservationExceptionHandler(
                (info, name, e) -> {
                  console.warn("Reader obs error: " + e.getMessage());
                  e.printStackTrace();
                });
            obs.addObserver(observer);
            obs.startCardDetection(ObservableCardReader.DetectionMode.REPEATING);

            for (int i = 1; i <= cycles; i++) {
              ctx.getConsole()
                  .waitForEnterOrTimeout(
                      "Cycle " + i + "/" + cycles + ": Insert the card", EVENT_TIMEOUT_MS);
              try {
                Thread.sleep(500);
              } catch (InterruptedException ignored) {
              }
              ctx.getConsole()
                  .waitForEnterOrTimeout(
                      "Cycle " + i + "/" + cycles + ": Remove the card", EVENT_TIMEOUT_MS);
              try {
                Thread.sleep(500);
              } catch (InterruptedException ignored) {
              }
            }

            obs.stopCardDetection();
            obs.removeObserver(observer);

            int i = inserts.get(), r = removals.get();
            console.info("Insertions: " + i + "  Removals: " + r + "  Expected: " + cycles);
            if (i == cycles && r == cycles) {
              return ScenarioResult.pass(
                  getId(), cycles + " insert+remove cycles detected correctly");
            }
            return ScenarioResult.fail(getId(), "Counts mismatch: inserts=" + i + " removals=" + r);
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M08.4";
          }

          public String getTitle() {
            return "stopCardDetection() stops further events";
          }

          public String getRequiredEquipment() {
            return "Any card";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            if (!(reader instanceof ObservableCardReader)) {
              return ScenarioResult.skip(getId(), "Reader is not observable");
            }
            ObservableCardReader obs = (ObservableCardReader) reader;
            AtomicInteger eventCount = new AtomicInteger(0);

            CardReaderObserverSpi observer =
                event -> {
                  eventCount.incrementAndGet();
                  if (event.getType() == CardReaderEvent.Type.CARD_INSERTED) {
                    reader.finalizeCardProcessing();
                  }
                };
            obs.setReaderObservationExceptionHandler(
                (info, name, e) -> {
                  console.warn("Reader obs error: " + e.getMessage());
                  e.printStackTrace();
                });
            obs.addObserver(observer);
            obs.startCardDetection(ObservableCardReader.DetectionMode.REPEATING);

            ctx.getConsole()
                .waitForEnterOrTimeout(
                    "Insert a card (to confirm detection is active)", EVENT_TIMEOUT_MS);
            try {
              Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }

            obs.stopCardDetection();
            console.info("stopCardDetection() called. Events before stop: " + eventCount.get());

            int countBeforeStop = eventCount.get();
            ctx.getConsole()
                .waitForEnterOrTimeout("Insert/remove card again (no event should fire)", 5_000);
            try {
              Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            int countAfterStop = eventCount.get();

            obs.removeObserver(observer);
            console.info("Events after stop: " + countAfterStop);

            if (countAfterStop == countBeforeStop) {
              return ScenarioResult.pass(getId(), "No new events after stopCardDetection()");
            }
            return ScenarioResult.fail(
                getId(),
                "Events still fired after stop: " + (countAfterStop - countBeforeStop) + " extra");
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M08.5";
          }

          public String getTitle() {
            return "Custom monitoring cycle duration (2000 ms)";
          }

          public String getRequiredEquipment() {
            return "Any card";
          }

          public ScenarioResult run(ValidationContext ctx) {
            // Re-register plugin with 2000 ms cycle
            try {
              ctx.getService().unregisterPlugin("PcscPlugin");
              ctx.setPlugin(
                  ctx.getService()
                      .registerPlugin(
                          PcscPluginFactoryBuilder.builder()
                              .setCardMonitoringCycleDuration(2000)
                              .build()));
              console.info("Plugin re-registered with monitoring cycle = 2000 ms");

              ObservableCardReader reader = ctx.selectReader();
              if (reader == null) {
                return ScenarioResult.skip(getId(), "No reader selected");
              }
              if (!(reader instanceof ObservableCardReader)) {
                return ScenarioResult.skip(getId(), "Reader is not observable");
              }
              ObservableCardReader obs = (ObservableCardReader) reader;
              CountDownLatch latch = new CountDownLatch(1);
              final long[] detectionDelay = {0};
              final long start = System.currentTimeMillis();

              CardReaderObserverSpi observer =
                  event -> {
                    if (event.getType() == CardReaderEvent.Type.CARD_INSERTED) {
                      detectionDelay[0] = System.currentTimeMillis() - start;
                      latch.countDown();
                      reader.finalizeCardProcessing();
                    }
                  };
              obs.setReaderObservationExceptionHandler(
                  (info, name, e) -> {
                    console.warn("Reader obs error: " + e.getMessage());
                    e.printStackTrace();
                  });
              obs.addObserver(observer);
              obs.startCardDetection(ObservableCardReader.DetectionMode.REPEATING);

              ctx.getConsole()
                  .waitForEnterOrTimeout("Insert a card on " + reader.getName(), EVENT_TIMEOUT_MS);
              boolean received = latch.await(5, TimeUnit.SECONDS);
              obs.stopCardDetection();
              obs.removeObserver(observer);

              // Restore default cycle
              ctx.getService().unregisterPlugin("PcscPlugin");
              ctx.setPlugin(
                  ctx.getService().registerPlugin(PcscPluginFactoryBuilder.builder().build()));

              if (received) {
                console.info("Detection delay: " + detectionDelay[0] + " ms");
                return ScenarioResult.pass(
                    getId(), "CARD_INSERTED with 2s cycle; delay=" + detectionDelay[0] + "ms");
              }
              return ScenarioResult.fail(getId(), "No event received");
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    return list;
  }
}
