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

/** Base class for the twelve validation modules. */
public abstract class AbstractModule {

  private final String id;
  private final String title;
  protected final Console console;
  private final List<Scenario> scenarios;

  protected AbstractModule(String id, String title, Console console) {
    this.id = id;
    this.title = title;
    this.console = console;
    this.scenarios = Collections.unmodifiableList(buildScenarios());
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public List<Scenario> getScenarios() {
    return scenarios;
  }

  /** Subclasses return the ordered list of scenarios for this module. */
  protected abstract List<Scenario> buildScenarios();

  /**
   * Runs the module in interactive mode: shows a scenario menu and executes the user's choice. The
   * user can run individual scenarios or all of them in sequence.
   */
  public void runInteractive(ValidationContext ctx) {
    console.header(id + " - " + title);

    while (true) {
      List<String> options = new ArrayList<>();
      options.add("Run ALL scenarios in this module");
      for (Scenario s : scenarios) {
        String eq =
            s.getRequiredEquipment().isEmpty() ? "" : "  [needs: " + s.getRequiredEquipment() + "]";
        options.add("[" + s.getId() + "] " + s.getTitle() + eq);
      }
      options.add("Back to main menu");

      int choice = console.selectIndex("Select a scenario:", options);

      if (choice == 0) {
        for (Scenario s : scenarios) {
          runScenario(s, ctx);
        }
      } else if (choice == options.size() - 1) {
        return;
      } else {
        runScenario(scenarios.get(choice - 1), ctx);
      }
    }
  }

  // ── Internal ─────────────────────────────────────────────────────────────────

  private void runScenario(Scenario scenario, ValidationContext ctx) {
    console.section("[" + scenario.getId() + "] " + scenario.getTitle());
    if (!scenario.getRequiredEquipment().isEmpty()) {
      console.info("Required equipment: " + scenario.getRequiredEquipment());
    }
    if (!ctx.isInitialized()) {
      ScenarioResult result = ScenarioResult.skip(scenario.getId(), "Plugin not initialised");
      console.result(result);
      ctx.record(result);
      return;
    }
    ScenarioResult result;
    try {
      result = scenario.run(ctx);
    } catch (Exception e) {
      result = ScenarioResult.fail(scenario.getId(), "Unexpected exception: " + e.getMessage());
    }
    console.result(result);
    ctx.record(result);
  }
}
