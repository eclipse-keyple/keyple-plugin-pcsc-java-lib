/* **************************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://www.calypsonet-asso.org/
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
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;

/**
 * (package-private)<br>
 * Implementation of {@link AbstractPcscPluginAdapter} suitable for platforms other than Windows.
 *
 * <p>Provides a createInstance method that receives a boolean as an argument to indicate that the
 * platform is MacOS. <br>
 * This information is used to create readers capable of handling the technical issues specific to
 * this platform.
 *
 * @since 2.0
 */
final class PcscPluginAdapter extends AbstractPcscPluginAdapter {

  /**
   * The 'volatile' qualifier ensures that read access to the object will only be allowed once the
   * object has been fully initialized. <br>
   * This qualifier is required for 'lazy-singleton' pattern with double-check method, to be
   * thread-safe.
   */
  private static volatile PcscPluginAdapter instance; // NOSONAR: lazy-singleton pattern.

  private final boolean isOsMac;

  /**
   * (private)<br>
   * Constructor.
   */
  private PcscPluginAdapter() {
    super(PcscPluginFactoryAdapter.PLUGIN_NAME);
    this.isOsMac = System.getProperty("os.name").toLowerCase().contains("mac");
  }

  /**
   * Gets the single instance of PcscPluginAdapter.
   *
   * @return single instance of PcscPluginAdapter
   * @since 2.0
   */
  static PcscPluginAdapter getInstance() {
    if (instance == null) {
      synchronized (PcscPluginAdapter.class) {
        if (instance == null) {
          instance = new PcscPluginAdapter();
        }
      }
    }
    return instance;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  CardTerminals getCardTerminals() {
    return TerminalFactory.getDefault().terminals();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a specific instance if the platform is MacOS, a standard one for others platforms
   * (e.g. Linux).
   *
   * @param terminal The smartcard.io {@link CardTerminal}.
   * @return A not null reference.
   * @since 2.0
   */
  @Override
  AbstractPcscReaderAdapter createReader(CardTerminal terminal) {
    if (isOsMac) {
      return new PcscReaderMacOsAdapter(terminal, this);
    } else {
      return new PcscReaderAdapter(terminal, this);
    }
  }
}
