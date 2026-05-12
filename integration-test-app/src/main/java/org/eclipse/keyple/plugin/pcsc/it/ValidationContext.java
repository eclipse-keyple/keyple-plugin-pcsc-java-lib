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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.keyple.core.service.Plugin;
import org.eclipse.keyple.core.service.SmartCardService;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keypop.reader.CardReader;
import org.eclipse.keypop.reader.ObservableCardReader;

/** Shared state passed to every scenario. */
public final class ValidationContext {

  private final Console console;
  private final List<ScenarioResult> results = new ArrayList<>();

  private SmartCardService service;
  private Plugin plugin;

  public ValidationContext(Console console) {
    this.console = console;
  }

  // ── Accessors ────────────────────────────────────────────────────────────────

  public Console getConsole() {
    return console;
  }

  public SmartCardService getService() {
    return service;
  }

  public void setService(SmartCardService service) {
    this.service = service;
  }

  public Plugin getPlugin() {
    return plugin;
  }

  public void setPlugin(Plugin plugin) {
    this.plugin = plugin;
  }

  public boolean isInitialized() {
    return service != null && plugin != null;
  }

  // ── Reader helpers ───────────────────────────────────────────────────────────

  /** Returns all readers currently registered with the plugin. */
  public Set<CardReader> getReaders() {
    if (plugin == null) return Collections.emptySet();
    return plugin.getReaders();
  }

  /**
   * Presents a numbered list of all connected readers and returns the one chosen by the user.
   * Returns {@code null} if no readers are available.
   */
  public ObservableCardReader selectReader() {
    if (plugin == null) {
      console.warn("Plugin not initialised.");
      return null;
    }
    Set<CardReader> readers = plugin.getReaders();
    if (readers.isEmpty()) {
      console.warn("No readers connected.");
      return null;
    }
    List<CardReader> list = new ArrayList<>(readers);
    List<String> labels = new ArrayList<>();
    for (CardReader r : list) {
      String type = r.isContactless() ? "[contactless]" : "[contact]";
      labels.add(r.getName() + "  " + type);
    }
    int idx = console.selectIndex("Select a reader:", labels);
    return (ObservableCardReader) list.get(idx);
  }

  /**
   * Convenience: returns the PcscReader extension for the given reader without needing to keep a
   * reference to the plugin at the call site.
   */
  public PcscReader getPcscReader(CardReader reader) {
    return plugin.getReaderExtension(PcscReader.class, reader.getName());
  }

  // ── Result tracking ──────────────────────────────────────────────────────────

  public void record(ScenarioResult result) {
    results.add(result);
  }

  public List<ScenarioResult> getResults() {
    return Collections.unmodifiableList(results);
  }

  /** Returns a one-line summary: "PASS: N FAIL: N SKIP: N TOTAL: N". */
  public String getSummary() {
    long pass = 0, fail = 0, skip = 0;
    for (ScenarioResult r : results) {
      switch (r.getStatus()) {
        case PASS:
          pass++;
          break;
        case FAIL:
          fail++;
          break;
        case SKIP:
          skip++;
          break;
      }
    }
    return "PASS: " + pass + "  FAIL: " + fail + "  SKIP: " + skip + "  TOTAL: " + results.size();
  }
}
