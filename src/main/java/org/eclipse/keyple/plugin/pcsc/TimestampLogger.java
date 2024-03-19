/* **************************************************************************************
 * Copyright (c) 2024 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.plugin.pcsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides a simple way to log timestamps for meaningful operations.
 *
 * @since 2.2.0
 */
class TimestampLogger {

  static final byte START = (byte) 0x00;
  static final byte STOP = (byte) 0xFF;

  private TimestampLogger() {}

  private static final ArrayList<Long> log = new ArrayList<Long>();

  /**
   * Adds an entry to the timestamp log with the current timestamp and an operation indicator.
   *
   * <p>The operation indicator is either START, STOP or the APDU instruction byte.
   *
   * @param operation The current operation.
   * @since 2.2.0
   */
  public static void addEntry(byte operation) {
    long timestamp = System.currentTimeMillis();
    long entry = (timestamp & 0x00FFFFFFFFFFFFFFL) | ((long) operation << 56);
    log.add(entry);
  }

  /**
   * Returns the timestamp log associated with the activity of the readers of the plugin.
   *
   * <p>The logger records the current timestamp and a byte value indicating the operation in
   * progress (0x00 for the card detection step, 0xFF for disconnection step, the INS code of the
   * current APDU for APDU transmission).
   *
   * <p>Two measurement points are recorded for the transmission of APDUs (before and after)
   * allowing to measure the card execution time.
   *
   * <p>The activity of all plugin readers feeds this list.
   *
   * @return A list of long values representing the timestamp log.
   * @since 2.2.0
   */
  public static List<Long> getLog() {
    return Collections.unmodifiableList(log);
  }

  /**
   * Resets the timestamp log by clearing all entries.
   *
   * @since 2.2.0
   */
  public static void reset() {
    log.clear();
  }
}
