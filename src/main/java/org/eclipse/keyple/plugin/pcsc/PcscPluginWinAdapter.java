/* **************************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://calypsonet.org/
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

import javax.smartcardio.CardTerminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link AbstractPcscPluginAdapter} suitable for Windows platforms.
 *
 * <p>Windows 8/10 platforms have a problem in the management of the smart card service combined
 * with Java smartcard.io. <br>
 * The service is stopped when the last connected reader is removed; this prevents the detection of
 * any new connection (SCARD_E_NO_SERVICE CardException). To overcome this problem a hack using
 * reflexivity is used to reset internal variables of smartcard.io.
 *
 * @since 2.0.0
 */
final class PcscPluginWinAdapter extends AbstractPcscPluginAdapter {

  private static final Logger logger = LoggerFactory.getLogger(PcscPluginWinAdapter.class);

  /**
   * Singleton instance of the class
   *
   * <p>'volatile' qualifier ensures that read access to the object will only be allowed once the
   * object has been fully initialized. <br>
   * This qualifier is required for "lazy-singleton" pattern with double-check method, to be
   * thread-safe.
   */
  private static volatile PcscPluginWinAdapter INSTANCE; // NOSONAR: lazy-singleton pattern.

  /**
   * (private)<br>
   * Creates the instance.
   */
  private PcscPluginWinAdapter() {
    super(PcscPluginFactoryAdapter.PLUGIN_NAME);
  }

  /**
   * Gets the single instance of PcscPluginWinAdapter.
   *
   * @return single instance of PcscPluginWinAdapter
   * @since 2.0.0
   */
  static PcscPluginWinAdapter getInstance() {
    if (INSTANCE == null) {
      synchronized (PcscPluginWinAdapter.class) {
        if (INSTANCE == null) {
          INSTANCE = new PcscPluginWinAdapter();
        }
      }
    }
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  AbstractPcscReaderAdapter createReader(CardTerminal terminal) {
    return new PcscReaderAdapter(terminal, this);
  }
}
