package org.eclipse.keyple.plugin.pcsc.validation;

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
