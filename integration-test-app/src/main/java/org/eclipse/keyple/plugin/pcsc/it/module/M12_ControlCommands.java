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
import org.eclipse.keyple.core.util.HexUtil;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.pcsc.it.AbstractModule;
import org.eclipse.keyple.plugin.pcsc.it.Console;
import org.eclipse.keyple.plugin.pcsc.it.Scenario;
import org.eclipse.keyple.plugin.pcsc.it.ScenarioResult;
import org.eclipse.keyple.plugin.pcsc.it.ValidationContext;
import org.eclipse.keypop.reader.CardReader;
import org.eclipse.keypop.reader.ObservableCardReader;

/** M12 - PC/SC control commands (IOCTL / CCID escape). */
public final class M12_ControlCommands extends AbstractModule {

  public M12_ControlCommands(Console console) {
    super("M12", "Control Commands", console);
  }

  @Override
  protected List<Scenario> buildScenarios() {
    List<Scenario> list = new ArrayList<>();

    list.add(
        new Scenario() {
          public String getId() {
            return "M12.1";
          }

          public String getTitle() {
            return "getIoctlCcidEscapeCommandId() returns platform-correct value";
          }

          public String getRequiredEquipment() {
            return "";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            PcscReader pcsc = ctx.getPcscReader(reader);
            int id = pcsc.getIoctlCcidEscapeCommandId();
            String os = System.getProperty("os.name", "").toLowerCase();
            boolean isWindows = os.contains("win");
            int expected = isWindows ? 3500 : 1;
            console.info("OS        : " + System.getProperty("os.name"));
            console.info("Expected  : " + expected);
            console.info("Returned  : " + id);
            if (id == expected) {
              return ScenarioResult.pass(
                  getId(),
                  "IOCTL CCID Escape ID = "
                      + id
                      + " (correct for "
                      + (isWindows ? "Windows" : "Linux/macOS")
                      + ")");
            }
            return ScenarioResult.fail(getId(), "Expected " + expected + " but got " + id);
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M12.2";
          }

          public String getTitle() {
            return "transmitControlCommand() GET_FIRMWARE_VERSION (reader-dependent)";
          }

          public String getRequiredEquipment() {
            return "CCID reader supporting firmware version command";
          }

          public ScenarioResult run(ValidationContext ctx) {
            ObservableCardReader reader = ctx.selectReader();
            if (reader == null) return ScenarioResult.skip(getId(), "No reader selected");
            PcscReader pcsc = ctx.getPcscReader(reader);
            // GET_FIRMWARE_VERSION command varies by reader vendor; use a common ACS command as
            // example
            // ACS ACR122U: 0xFF 0x00 0x48 0x00 0x00
            byte[] cmd;
            String cmdHex =
                ctx.getConsole()
                    .prompt(
                        "Enter control command bytes (hex) [ENTER for ACS GET_FIRMWARE: FF004800 00]");
            if (cmdHex.isEmpty()) {
              cmd = new byte[] {(byte) 0xFF, 0x00, 0x48, 0x00, 0x00};
            } else {
              try {
                cmd = HexUtil.toByteArray(cmdHex);
              } catch (Exception e) {
                return ScenarioResult.fail(getId(), "Invalid hex: " + e.getMessage());
              }
            }
            try {
              int commandId = pcsc.getIoctlCcidEscapeCommandId();
              console.info("CommandId : " + commandId);
              console.info("CMD       : " + HexUtil.toHex(cmd));
              byte[] response = pcsc.transmitControlCommand(commandId, cmd);
              console.info("RSP       : " + HexUtil.toHex(response));
              return ScenarioResult.pass(getId(), "Control response: " + HexUtil.toHex(response));
            } catch (Exception e) {
              console.warn(
                  "Command failed (reader may not support this command): " + e.getMessage());
              return ScenarioResult.skip(
                  getId(), "Reader rejected control command: " + e.getMessage());
            }
          }
        });

    list.add(
        new Scenario() {
          public String getId() {
            return "M12.3";
          }

          public String getTitle() {
            return "Two readers operated simultaneously";
          }

          public String getRequiredEquipment() {
            return "Two PC/SC readers, one card each";
          }

          public ScenarioResult run(ValidationContext ctx) {
            List<CardReader> readers = new ArrayList<>(ctx.getReaders());
            if (readers.size() < 2) {
              return ScenarioResult.skip(
                  getId(), "Need at least 2 readers (" + readers.size() + " found)");
            }
            CardReader r1 = readers.get(0);
            CardReader r2 = readers.get(1);
            console.info("Reader 1: " + r1.getName());
            console.info("Reader 2: " + r2.getName());
            ctx.getConsole().waitForEnter("Insert a card into each reader");

            ReaderSpi spi1 = (ReaderSpi) ctx.getPcscReader(r1);
            ReaderSpi spi2 = (ReaderSpi) ctx.getPcscReader(r2);
            try {
              boolean p1 = spi1.isCardPresent();
              boolean p2 = spi2.isCardPresent();
              console.info("Reader 1 present=" + p1 + " ATR: " + spi1.getPowerOnData());
              console.info("Reader 2 present=" + p2 + " ATR: " + spi2.getPowerOnData());
              ((ObservableReaderSpi) spi1).deselectCard();
              ((ObservableReaderSpi) spi2).deselectCard();
              if (p1 && p2) {
                return ScenarioResult.pass(
                    getId(), "Both readers operated simultaneously without conflict");
              }
              return ScenarioResult.fail(getId(), "r1=" + p1 + " r2=" + p2);
            } catch (Exception e) {
              return ScenarioResult.fail(getId(), e.getMessage());
            }
          }
        });

    return list;
  }
}
