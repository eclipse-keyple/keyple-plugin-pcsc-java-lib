/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://www.calypsonet-asso.org/
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
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.WaitForCardInsertionNonBlockingSpi;

/**
 * (package-private)<br>
 * Implementation of AbstractPcscReaderAdapter suitable for the MacOS platforms.
 *
 * <p>Due to some issues with Apple's implementation of smartcard.io, card insertion detection is
 * performed by the card presence test rather than the usual waiting method ({@link
 * WaitForCardInsertionNonBlockingSpi}).
 *
 * @since 2.0
 */
final class PcscReaderMacOsAdapter extends AbstractPcscReaderAdapter
    implements WaitForCardInsertionNonBlockingSpi {

  /**
   * (package-private)<br>
   *
   * @since 2.0
   */
  PcscReaderMacOsAdapter(CardTerminal terminal, AbstractPcscPluginAdapter pluginAdapter) {
    super(terminal, pluginAdapter);
  }
}
