/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.plugin.pcsc;

import java.util.List;
import org.eclipse.keyple.core.common.KeyplePluginExtension;

/**
 * PC/SC specific {@link KeyplePluginExtension}.
 *
 * @since 2.0.0
 */
public interface PcscPlugin extends KeyplePluginExtension {

  /**
   * Returns the timestamp log associated with the activity of the readers of the plugin.
   *
   * <p>The logger records the current timestamp and a byte value indicating the operation in
   * progress (0x00 for the card detection step, 0xFF for disconnection step, the INS code of the
   * current APDU for APDU transmission).
   *
   * <p>Two measurement points are recorded for the transmission of APDUs (before and after)
   * allowing to measure the card execution time.
   *
   * @return A list of long values.
   * @since 2.2.0
   */
  List<Long> getTimestampLog();

  /**
   * Clears the timestamp log associated with the activity of the readers of the plugin.
   *
   * @since 2.2.0
   */
  void clearTimestampLog();
}
