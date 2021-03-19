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

import javax.smartcardio.Card;
import org.eclipse.keyple.core.common.KeypleReaderExtension;
import org.eclipse.keyple.core.plugin.ReaderIOException;
import org.eclipse.keyple.core.service.Reader;

/**
 * PC/SC reader specific interface.
 *
 * <p>Provides specific settings and methods for configuring a PC/SC reader.
 *
 * @since 2.0
 */
public interface PcscReader extends KeypleReaderExtension {

  /**
   * Connection mode to indicate if the application is willing to share the card with other
   * applications.
   *
   * <p>Corresponds to the beginExclusive() and endExclusive() methods of smartcard.io and, at a
   * lower level, to the connection mode defined by PC/SC and used in the SCardConnect function.
   *
   * @since 2.0
   */
  enum SharingMode {
    /**
     * Allows simultaneous access to the card
     *
     * @since 2.0
     */
    SHARED,
    /**
     * Requests exclusive access to the card
     *
     * @since 2.0
     */
    EXCLUSIVE
  }

  /**
   * Available transmission protocols as defined by the PC/SC standard.
   *
   * @since 2.0
   */
  enum IsoProtocol {

    /**
     * to connect using any available protocol
     *
     * @since 2.0
     */
    ANY("*"),
    /**
     * to connect using T=0 protocol
     *
     * @since 2.0
     */
    T0("T=0"),
    /**
     * to connect using T=1 protocol
     *
     * @since 2.0
     */
    T1("T=1"),
    /**
     * to connect using T=CL protocol
     *
     * @since 2.0
     */
    TCL("T=CL");

    private final String value;

    /**
     * Constructor.
     *
     * <p>Associates the enum value with its corresponding definition in the PC/SC standard.
     *
     * @param value A string
     * @since 2.0
     */
    IsoProtocol(String value) {
      this.value = value;
    }

    /**
     * Gets the string expected by smartcard.io / PC/SC to set the card transmission protocol.
     *
     * @return A not empty string.
     * @since 2.0
     */
    public String getValue() {
      return value;
    }
  }

  /**
   * Action to be taken after the card is disconnected.
   *
   * @since 2.0
   */
  enum DisconnectionMode {
    /**
     * Resets the card
     *
     * @since 2.0
     */
    RESET,
    /**
     * Keeps the status of the card unchanged
     *
     * @since 2.0
     */
    LEAVE
  }

  /**
   * Changes the PC/SC sharing mode (default value {@link SharingMode#EXCLUSIVE}).
   *
   * <p>This mode will be used when a new {@link Card} is created.
   *
   * <p>If a card is already inserted, changes immediately the mode in the current {@link Card}
   * object.
   *
   * @param sharingMode The {@link SharingMode} to use (must be not null).
   * @return This instance.
   * @throws IllegalArgumentException If sharingMode is null
   * @throws ReaderIOException If the sharing mode setting failed.
   * @since 2.0
   */
  PcscReader setSharingMode(SharingMode sharingMode) throws ReaderIOException;

  /**
   * Sets the reader transmission mode.
   *
   * <p>A PC/SC reader can be contact or contactless. There is no way by generic programming to know
   * what type of technology a reader uses.
   *
   * <p>Thus, it is the responsibility of the application to give the reader the means to know his
   * own type. This information will be used by the {@link Reader#isContactless()} mode method.<br>
   * This can be achieved with this method but also by giving the plugin the means to determine the
   * type from the reader's name. In the latter case, the application does not need to call this
   * method, the reader itself will determine its type using the plugin's parameters (see {@link
   * PcscPluginFactoryBuilder}.
   *
   * <p>The default value for this parameter if this method is not called is undefined.<br>
   * The {@link Reader#isContactless()} may raise an {@link IllegalStateException}.
   *
   * @param contactless true to set contactless mode, false to set contact mode.
   * @return This instance.
   * @since 2.0
   */
  PcscReader setContactless(boolean contactless);

  /**
   * Changes the protocol to be used by the PC/SC reader when connecting to the card ({@link
   * IsoProtocol#T0}, {@link IsoProtocol#T1}, or {@link IsoProtocol#TCL}), or {@link
   * IsoProtocol#ANY} to connect using any available protocol (default value {@link
   * IsoProtocol#ANY}).
   *
   * @param isoProtocol The {@link IsoProtocol} to use (must be not null).
   * @return This instance.
   * @throws IllegalArgumentException If isoProtocol is null
   * @since 2.0
   */
  PcscReader setIsoProtocol(IsoProtocol isoProtocol);

  /**
   * Changes the action to be taken after disconnection (default value {@link
   * DisconnectionMode#LEAVE}).
   *
   * <p>The cardis either reset or left as is.
   *
   * <p>The default value for this parameter if this method is not called is {@link
   * DisconnectionMode#LEAVE}.
   *
   * @param disconnectionMode The {@link DisconnectionMode} to use (must be not null).
   * @return This instance.
   * @throws IllegalArgumentException If disconnectionMode is null
   */
  PcscReader setDisconnectionMode(DisconnectionMode disconnectionMode);
}
