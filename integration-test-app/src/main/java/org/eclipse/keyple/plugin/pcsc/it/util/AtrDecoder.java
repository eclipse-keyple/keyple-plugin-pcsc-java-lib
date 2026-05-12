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
package org.eclipse.keyple.plugin.pcsc.it.util;

/** Utility methods for decoding ATR (Answer To Reset) data. */
public final class AtrDecoder {

  private AtrDecoder() {}

  /** Returns a human-readable summary of the ATR hex string. */
  public static String describe(String atrHex) {
    if (atrHex == null || atrHex.isEmpty()) {
      return "(no ATR)";
    }
    return "ATR: " + atrHex + "  (" + atrHex.length() / 2 + " bytes)";
  }

  /**
   * Returns the byte at the given zero-based position in the ATR hex string, or -1 if the position
   * is out of range.
   */
  public static int byteAt(String atrHex, int index) {
    if (atrHex == null || index * 2 + 2 > atrHex.length()) return -1;
    return Integer.parseInt(atrHex.substring(index * 2, index * 2 + 2), 16);
  }
}
