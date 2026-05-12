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

/** A single test case within a validation module. */
public interface Scenario {

  /** Short identifier used in the report (e.g. "M03.2"). */
  String getId();

  /** One-line description shown in the scenario menu. */
  String getTitle();

  /**
   * Description of the hardware required to run this scenario (empty string if none beyond a
   * connected reader).
   */
  String getRequiredEquipment();

  /**
   * Runs the scenario.
   *
   * <p>Implementations must not throw unchecked exceptions; they should catch them and return a
   * {@link ScenarioResult#fail} result instead.
   */
  ScenarioResult run(ValidationContext ctx);
}
