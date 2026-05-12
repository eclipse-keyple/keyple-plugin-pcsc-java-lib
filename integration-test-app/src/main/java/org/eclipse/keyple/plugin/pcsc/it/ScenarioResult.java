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

/** Outcome of a single validation scenario. */
public final class ScenarioResult {

  public enum Status {
    PASS,
    FAIL,
    SKIP
  }

  private final String scenarioId;
  private final Status status;
  private final String details;

  private ScenarioResult(String scenarioId, Status status, String details) {
    this.scenarioId = scenarioId;
    this.status = status;
    this.details = details;
  }

  public static ScenarioResult pass(String id, String details) {
    return new ScenarioResult(id, Status.PASS, details);
  }

  public static ScenarioResult fail(String id, String details) {
    return new ScenarioResult(id, Status.FAIL, details);
  }

  public static ScenarioResult skip(String id, String reason) {
    return new ScenarioResult(id, Status.SKIP, reason);
  }

  public String getScenarioId() {
    return scenarioId;
  }

  public Status getStatus() {
    return status;
  }

  public String getDetails() {
    return details;
  }

  @Override
  public String toString() {
    return "[" + status + "] " + scenarioId + " - " + details;
  }
}
