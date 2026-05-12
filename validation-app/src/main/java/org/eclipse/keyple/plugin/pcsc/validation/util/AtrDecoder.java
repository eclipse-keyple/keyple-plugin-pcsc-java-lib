package org.eclipse.keyple.plugin.pcsc.validation.util;

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
   * Returns the byte at the given zero-based position in the ATR hex string, or -1 if the
   * position is out of range.
   */
  public static int byteAt(String atrHex, int index) {
    if (atrHex == null || index * 2 + 2 > atrHex.length()) return -1;
    return Integer.parseInt(atrHex.substring(index * 2, index * 2 + 2), 16);
  }
}
