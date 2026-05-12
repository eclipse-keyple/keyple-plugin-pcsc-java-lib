package org.eclipse.keyple.plugin.pcsc.validation;

import javax.smartcardio.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Standalone test for javax.smartcardio terminal.isCardPresent().
 *
 * <p>Lists available PC/SC terminals, selects the first one (or the one whose name contains the
 * argument), then polls isCardPresent() every 500 ms and logs each state change together with the
 * ATR when a card is detected.
 *
 * <p>Run: java -cp ... CardPresenceTest [terminal-name-fragment]
 * Press Ctrl+C to stop.
 */
public class CardPresenceTest {

  private static final int POLL_INTERVAL_MS = 5;
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  public static void main(String[] args) throws Exception {
    TerminalFactory factory = TerminalFactory.getDefault();
    List<CardTerminal> terminals = factory.terminals().list();

    if (terminals.isEmpty()) {
      System.out.println("No PC/SC terminal found.");
      return;
    }

    System.out.println("Available terminals:");
    for (int i = 0; i < terminals.size(); i++) {
      System.out.printf("  [%d] %s%n", i, terminals.get(i).getName());
    }

    CardTerminal terminal = selectTerminal(terminals, args.length > 0 ? args[0] : null);
    System.out.printf("%nMonitoring: %s%n", terminal.getName());
    System.out.println("Press Ctrl+C to stop.\n");

    boolean wasPresent = false;

    while (!Thread.currentThread().isInterrupted()) {
      boolean isPresent = terminal.isCardPresent();

      if (isPresent != wasPresent) {
        if (isPresent) {
          System.out.printf("%s [+] Card PRESENT  -- ATR: %s%n", ts(), readAtr(terminal));
        } else {
          System.out.printf("%s [-] Card ABSENT%n", ts());
        }
        wasPresent = isPresent;
      }

      Thread.sleep(POLL_INTERVAL_MS);
    }
  }

  private static String ts() {
    return LocalTime.now().format(TS);
  }

  private static CardTerminal selectTerminal(List<CardTerminal> terminals, String fragment) {
    if (fragment != null) {
      for (CardTerminal t : terminals) {
        if (t.getName().toLowerCase().contains(fragment.toLowerCase())) {
          System.out.printf("Selected: %s%n", t.getName());
          return t;
        }
      }
      System.out.printf("No terminal matching '%s', using first one.%n", fragment);
    }
    return terminals.get(0);
  }

  private static String readAtr(CardTerminal terminal) {
    try {
      Card card = terminal.connect("*");
      byte[] atrBytes = card.getATR().getBytes();
      card.disconnect(false);
      return bytesToHex(atrBytes);
    } catch (CardException e) {
      return "(ATR unavailable: " + e.getMessage() + ")";
    }
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02X", b));
    }
    return sb.toString();
  }
}
