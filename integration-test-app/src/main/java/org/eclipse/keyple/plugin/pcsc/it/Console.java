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
package org.eclipse.keyple.plugin.pcsc.it;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Terminal I/O helper with optional ANSI colour support. */
public final class Console {

  // ESC character (0x1B) written as a literal so the source file remains ASCII-safe
  private static final String E = String.valueOf((char) 27);
  private static final String RESET = E + "[0m";
  private static final String BOLD = E + "[1m";
  private static final String DIM = E + "[2m";
  private static final String BRIGHT_RED = E + "[91m";
  private static final String BRIGHT_GREEN = E + "[92m";
  private static final String YELLOW = E + "[33m";
  private static final String CYAN = E + "[36m";

  // All System.in reads happen in this single daemon thread; the main thread polls the queue.
  // This prevents the race condition that arises when waitForEnterOrTimeout times out and
  // leaves an abandoned thread still blocking on Scanner.nextLine() while the main thread
  // tries to read the next line.
  private final BlockingQueue<String> lineQueue = new LinkedBlockingQueue<>();
  private final boolean ansi;

  public Console() {
    this.ansi = isAnsiSupported();

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    Thread readerThread =
        new Thread(
            () -> {
              try {
                String line;
                while ((line = br.readLine()) != null) {
                  lineQueue.put(line);
                }
              } catch (Exception ignored) {
              }
            });
    readerThread.setDaemon(true);
    readerThread.setName("console-stdin-reader");
    readerThread.start();
  }

  // ── Layout ──────────────────────────────────────────────────────────────────

  public void header(String text) {
    System.out.println();
    System.out.println(c(BOLD + CYAN, repeat('=', 64)));
    System.out.println(c(BOLD + CYAN, "  " + text));
    System.out.println(c(BOLD + CYAN, repeat('=', 64)));
  }

  public void section(String text) {
    System.out.println();
    System.out.println(c(BOLD + CYAN, "  --- " + text));
  }

  public void println(String msg) {
    System.out.println("  " + msg);
  }

  public void info(String msg) {
    System.out.println("    " + msg);
  }

  // ── Status messages ──────────────────────────────────────────────────────────

  public void pass(String msg) {
    System.out.println("  " + c(BRIGHT_GREEN, "[PASS] " + msg));
  }

  public void fail(String msg) {
    System.out.println("  " + c(BRIGHT_RED, "[FAIL] " + msg));
  }

  public void warn(String msg) {
    System.out.println("  " + c(YELLOW, "[WARN] " + msg));
  }

  public void skip(String msg) {
    System.out.println("  " + c(DIM, "[SKIP] " + msg));
  }

  public void result(ScenarioResult r) {
    String line = r.getScenarioId() + " - " + r.getDetails();
    switch (r.getStatus()) {
      case PASS:
        pass(line);
        break;
      case FAIL:
        fail(line);
        break;
      case SKIP:
        skip(line);
        break;
    }
  }

  /** Prints a coloured summary line: PASS count in green, FAIL count in red. */
  public void summary(long pass, long fail, long skip) {
    long total = pass + fail + skip;
    String text =
        c(BRIGHT_GREEN, "PASS: " + pass)
            + "  "
            + c(BRIGHT_RED, "FAIL: " + fail)
            + "  "
            + c(DIM, "SKIP: " + skip)
            + "  TOTAL: "
            + total;
    System.out.println("  " + text);
  }

  // ── User interaction ─────────────────────────────────────────────────────────

  /** Displays an instruction and waits for the user to press ENTER. */
  public void waitForEnter(String instruction) {
    System.out.println();
    System.out.print(c(YELLOW, "  >>> ") + instruction + "  [ENTER]: ");
    takeLine();
  }

  /**
   * Displays an instruction then waits for ENTER or times out after {@code timeoutMs} ms.
   *
   * @return true if the user pressed ENTER, false if the timeout elapsed.
   */
  public boolean waitForEnterOrTimeout(String instruction, long timeoutMs) {
    System.out.println();
    System.out.println(c(YELLOW, "  >>> ") + instruction);
    System.out.println(
        c(DIM, "      (press ENTER when done, or wait " + (timeoutMs / 1000) + "s)"));

    try {
      boolean pressed = lineQueue.poll(timeoutMs, TimeUnit.MILLISECONDS) != null;
      System.out.println();
      return pressed;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.out.println();
      return false;
    }
  }

  /** Asks a yes/no question; returns true for 'y' / 'yes'. */
  public boolean confirm(String question) {
    System.out.print(c(YELLOW, "  >>> ") + question + " [y/N]: ");
    String answer = takeLine().trim().toLowerCase();
    return answer.equals("y") || answer.equals("yes");
  }

  /** Reads a free-form string from the user. */
  public String prompt(String label) {
    System.out.print(c(YELLOW, "  >>> ") + label + ": ");
    return takeLine().trim();
  }

  /**
   * Presents a numbered list and returns the zero-based index of the chosen item. If there is only
   * one item it is auto-selected without prompting.
   */
  public int selectIndex(String label, List<String> options) {
    if (options.size() == 1) {
      System.out.println("  " + label + " -> " + options.get(0) + " (auto-selected)");
      return 0;
    }
    System.out.println();
    System.out.println(c(CYAN, "  " + label));
    for (int i = 0; i < options.size(); i++) {
      System.out.println("    [" + (i + 1) + "] " + options.get(i));
    }
    while (true) {
      System.out.print(c(YELLOW, "  >>> ") + "Choice (1-" + options.size() + "): ");
      try {
        int choice = Integer.parseInt(takeLine().trim());
        if (choice >= 1 && choice <= options.size()) {
          return choice - 1;
        }
      } catch (NumberFormatException ignored) {
      }
      System.out.println("    Invalid choice, please try again.");
    }
  }

  // ── Internal ─────────────────────────────────────────────────────────────────

  private static boolean isAnsiSupported() {
    String prop = System.getProperty("ansi.colors");
    if ("true".equalsIgnoreCase(prop)) return true; // -Dansi.colors=true  → force on
    if ("false".equalsIgnoreCase(prop)) return false; // -Dansi.colors=false → force off
    // Auto-detect
    String os = System.getProperty("os.name", "").toLowerCase();
    if (!os.contains("windows")) return true;
    // Windows: detect known ANSI-capable hosts
    return System.getenv("WT_SESSION") != null // Windows Terminal
        || System.getenv("ANSICON") != null // ANSICON
        || "JetBrains-JediTerm".equals(System.getenv("TERMINAL_EMULATOR")); // IntelliJ Terminal tab
  }

  private String takeLine() {
    try {
      return lineQueue.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "";
    }
  }

  private String c(String code, String text) {
    return ansi ? code + text + RESET : text;
  }

  private static String repeat(char ch, int count) {
    StringBuilder sb = new StringBuilder(count);
    for (int i = 0; i < count; i++) sb.append(ch);
    return sb.toString();
  }
}
