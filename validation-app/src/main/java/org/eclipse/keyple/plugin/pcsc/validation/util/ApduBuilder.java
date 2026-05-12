package org.eclipse.keyple.plugin.pcsc.validation.util;

/** Standard APDU command builders. */
public final class ApduBuilder {

  private ApduBuilder() {}

  /** SELECT MF (ISO 7816-4 §11.2 - works on most cards). */
  public static byte[] selectMasterFile() {
    return new byte[] {0x00, (byte) 0xA4, 0x00, 0x00};
  }

  /** SELECT by AID (ISO 7816-4 §11.2). */
  public static byte[] selectByAid(byte[] aid) {
    byte[] cmd = new byte[5 + aid.length];
    cmd[0] = 0x00;
    cmd[1] = (byte) 0xA4;
    cmd[2] = 0x04;
    cmd[3] = 0x00;
    cmd[4] = (byte) aid.length;
    System.arraycopy(aid, 0, cmd, 5, aid.length);
    return cmd;
  }

  /**
   * GET CHALLENGE (ISO 7816-4 §7.5.3 - generates an 8-byte random number). Works on most smart
   * cards and does not modify any card data.
   */
  public static byte[] getChallenge() {
    return new byte[] {0x00, (byte) 0x84, 0x00, 0x00, 0x08};
  }

  /** READ BINARY from offset 0 (ISO 7816-4 §7.2.3). */
  public static byte[] readBinary(int length) {
    return new byte[] {0x00, (byte) 0xB0, 0x00, 0x00, (byte) (length & 0xFF)};
  }

  /** GET DATA (ISO 7816-4 §7.4.2) for tag P1P2. */
  public static byte[] getData(byte p1, byte p2) {
    return new byte[] {0x00, (byte) 0xCA, p1, p2, 0x00};
  }

  /** Parses the two-byte status word from the end of the APDU response. */
  public static int sw(byte[] response) {
    if (response == null || response.length < 2) return -1;
    return ((response[response.length - 2] & 0xFF) << 8) | (response[response.length - 1] & 0xFF);
  }

  /** Returns a hex representation of the status word, e.g. "9000". */
  public static String swHex(byte[] response) {
    int sw = sw(response);
    if (sw < 0) return "????";
    return String.format("%04X", sw);
  }

  /** Returns true if SW == 0x9000 or SW1 == 0x61 or SW1 == 0x9x (ISO "OK" variants). */
  public static boolean isOk(byte[] response) {
    int sw = sw(response);
    if (sw < 0) return false;
    int sw1 = (sw >> 8) & 0xFF;
    return sw == 0x9000 || sw1 == 0x61 || (sw1 >= 0x90 && sw1 <= 0x9F);
  }
}
