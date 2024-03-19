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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.smartcardio.*;
import org.eclipse.keyple.core.plugin.CardIOException;
import org.eclipse.keyple.core.plugin.ReaderIOException;
import org.eclipse.keyple.core.plugin.TaskCanceledException;
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.CardRemovalWaiterBlockingSpi;
import org.eclipse.keyple.core.util.Assert;
import org.eclipse.keyple.core.util.HexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for all PC/SC reader adapters.
 *
 * @since 2.0.0
 */
class AbstractPcscReaderAdapter
    implements PcscReader,
        ConfigurableReaderSpi,
        ObservableReaderSpi,
        CardRemovalWaiterBlockingSpi {

  private static final Logger logger = LoggerFactory.getLogger(AbstractPcscReaderAdapter.class);

  private final CardTerminal terminal;
  private final String name;
  private final AbstractPcscPluginAdapter pluginAdapter;
  private final boolean isWindows;
  private Card card;
  private CardChannel channel;
  private Boolean isContactless;
  private String protocol = IsoProtocol.ANY.getValue();
  private boolean isModeExclusive = true;
  private DisconnectionMode disconnectionMode = DisconnectionMode.RESET;

  // the latency delay value (in ms) determines the maximum time during which the
  // waitForCardAbsent blocking functions will execute.
  // This will correspond to the capacity to react to the interrupt signal of
  // the thread (see cancel method of the Future object)
  private static final long REMOVAL_LATENCY = 500;
  private final AtomicBoolean loopWaitCardRemoval = new AtomicBoolean();

  /**
   * Creates an instance the class, keeps the terminal and parent plugin, extract the reader name
   * from the terminal.
   *
   * @param terminal The terminal from smartcard.io
   * @param pluginAdapter The reference to the parent plugin.
   * @since 2.0.0
   */
  AbstractPcscReaderAdapter(CardTerminal terminal, AbstractPcscPluginAdapter pluginAdapter) {
    this.terminal = terminal;
    this.pluginAdapter = pluginAdapter;
    this.name = terminal.getName();
    this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
  }

  /**
   * Gets the smartcard.io terminal.
   *
   * @return A not null reference.
   */
  final CardTerminal getTerminal() {
    return terminal;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final String getName() {
    return name;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final boolean isProtocolSupported(String readerProtocol) {
    return pluginAdapter.getProtocolRule(readerProtocol) != null;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void activateProtocol(String readerProtocol) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "{}: activating the {} protocol causes no action to be taken.",
          getName(),
          readerProtocol);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void deactivateProtocol(String readerProtocol) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "{}: deactivating the {} protocol causes no action to be taken.",
          getName(),
          readerProtocol);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws PatternSyntaxException If the expression's syntax is invalid
   * @since 2.0.0
   */
  @Override
  public final boolean isCurrentProtocol(String readerProtocol) {
    String protocolRule = pluginAdapter.getProtocolRule(readerProtocol);
    boolean isCurrentProtocol;
    if (protocolRule != null && !protocolRule.isEmpty()) {
      String atr = HexUtil.toHex(card.getATR().getBytes());
      isCurrentProtocol = Pattern.compile(protocolRule).matcher(atr).matches();
    } else {
      isCurrentProtocol = false;
    }
    return isCurrentProtocol;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The physical channel is open using the current sharing mode.
   *
   * @see #setSharingMode(SharingMode)
   * @since 2.0.0
   */
  @Override
  public final void openPhysicalChannel() throws ReaderIOException {
    /* init of the card physical channel: if not yet established, opening of a new physical channel */
    try {
      if (card == null) {
        logger.debug(
            "{}: opening of a card physical channel for protocol '{}'", this.getName(), protocol);
        this.card = this.terminal.connect(protocol);
        if (isModeExclusive) {
          card.beginExclusive();
          logger.debug("{}: opening of a card physical channel in exclusive mode.", this.getName());
        } else {
          logger.debug("{}: opening of a card physical channel in shared mode.", this.getName());
        }
      }
      this.channel = card.getBasicChannel();
    } catch (CardException e) {
      throw new ReaderIOException(getName() + ": Error while opening Physical Channel", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void closePhysicalChannel() throws ReaderIOException {
    try {
      if (card != null) {
        channel = null;
        card.disconnect(disconnectionMode == DisconnectionMode.RESET);
        card = null;
      } else {
        logger.debug(
            "{}: card object found null when closing the physical channel.", this.getName());
      }
    } catch (CardException e) {
      throw new ReaderIOException("Error while closing physical channel", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final boolean isPhysicalChannelOpen() {
    return card != null;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final boolean checkCardPresence() throws ReaderIOException {
    try {
      return terminal.isCardPresent();
    } catch (CardException e) {
      throw new ReaderIOException("Exception occurred in isCardPresent", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>In the case of a PC/SC reader, the power-on data is provided by the reader in the form of an
   * ATR ISO7816 structure whatever the card.
   *
   * @since 2.0.0
   */
  @Override
  public final String getPowerOnData() {
    return HexUtil.toHex(card.getATR().getBytes());
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final byte[] transmitApdu(byte[] apduCommandData)
      throws ReaderIOException, CardIOException {
    byte[] apduResponseData;
    if (channel != null) {
      try {
        apduResponseData = channel.transmit(new CommandAPDU(apduCommandData)).getBytes();
      } catch (CardException e) {
        if (e.getMessage().contains("REMOVED")) {
          throw new CardIOException(this.getName() + ":" + e.getMessage(), e);
        } else {
          throw new ReaderIOException(this.getName() + ":" + e.getMessage(), e);
        }
      } catch (IllegalStateException e) {
        // card could have been removed prematurely
        throw new CardIOException(this.getName() + ":" + e.getMessage(), e);
      }
    } else {
      // could occur if the card was removed
      throw new CardIOException(this.getName() + ": null channel.");
    }
    return apduResponseData;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final boolean isContactless() {
    if (isContactless == null) {
      // First time initialisation, the transmission mode has not yet been determined or fixed
      // explicitly, let's ask the plugin to determine it (only once)
      isContactless = pluginAdapter.isContactless(getName());
    }
    return isContactless;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void onUnregister() {
    /* Nothing to do here in this reader */
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void onStartDetection() {
    /* Nothing to do here in this reader */
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void onStopDetection() {
    /* Nothing to do here in this reader */
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default value is {@link SharingMode#EXCLUSIVE}.
   *
   * @since 2.0.0
   */
  @Override
  public final PcscReader setSharingMode(SharingMode sharingMode) {
    Assert.getInstance().notNull(sharingMode, "sharingMode");
    if (logger.isTraceEnabled()) {
      logger.trace("{}: set sharing mode to {}", getName(), sharingMode.name());
    }
    if (sharingMode == SharingMode.SHARED) {
      // if a card is present, change the mode immediately
      if (card != null) {
        try {
          card.endExclusive();
        } catch (CardException e) {
          throw new IllegalStateException("Couldn't disable exclusive mode", e);
        }
      }
      isModeExclusive = false;
    } else if (sharingMode == SharingMode.EXCLUSIVE) {
      isModeExclusive = true;
    }
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final PcscReader setContactless(boolean contactless) {
    if (logger.isTraceEnabled()) {
      logger.trace("{}: set contactless type: {}", getName(), contactless);
    }
    this.isContactless = contactless;
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default value is {@link IsoProtocol#ANY}.
   *
   * @since 2.0.0
   */
  @Override
  public final PcscReader setIsoProtocol(IsoProtocol isoProtocol) {
    Assert.getInstance().notNull(isoProtocol, "isoProtocol");
    if (logger.isTraceEnabled()) {
      logger.trace(
          "{}: set ISO protocol to {} ({})", getName(), isoProtocol.name(), isoProtocol.getValue());
    }
    protocol = isoProtocol.getValue();
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final PcscReader setDisconnectionMode(DisconnectionMode disconnectionMode) {
    Assert.getInstance().notNull(disconnectionMode, "disconnectionMode");
    if (logger.isTraceEnabled()) {
      logger.trace("{}: set disconnection to {}", getName(), disconnectionMode.name());
    }
    this.disconnectionMode = disconnectionMode;
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void waitForCardRemoval() throws TaskCanceledException, ReaderIOException {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "{}: start waiting for the removal of the card in a loop with a latency of {} ms.",
          this.getName(),
          REMOVAL_LATENCY);
    }

    // activate loop
    loopWaitCardRemoval.set(true);

    try {
      while (loopWaitCardRemoval.get()) {
        if (getTerminal().waitForCardAbsent(REMOVAL_LATENCY)) {
          // card removed
          if (logger.isTraceEnabled()) {
            logger.trace("{}: card removed.", this.getName());
          }
          return;
        }
        if (Thread.interrupted()) {
          break;
        }
      }
    } catch (CardException e) {
      // here, it is a communication failure with the reader
      throw new ReaderIOException(
          this.getName() + ": an error occurred while waiting for the card removal.", e);
    }
    throw new TaskCanceledException(
        this.getName() + ": the wait for the card removal task has been cancelled.");
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void stopWaitForCardRemoval() {
    if (logger.isTraceEnabled()) {
      logger.trace("{}: stop waiting for the card removal requested.", this.getName());
    }
    loopWaitCardRemoval.set(false);
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.1.0
   */
  @Override
  public byte[] transmitControlCommand(int commandId, byte[] command) {
    Assert.getInstance().notNull(command, "command");
    byte[] response;
    int controlCode = isWindows ? 0x00310000 | (commandId << 2) : 0x42000000 | commandId;
    try {
      if (card != null) {
        response = card.transmitControlCommand(controlCode, command);
      } else {
        Card virtualCard = terminal.connect("DIRECT");
        response = virtualCard.transmitControlCommand(controlCode, command);
        virtualCard.disconnect(false);
      }
    } catch (CardException e) {
      throw new IllegalStateException("Reader failure.", e);
    }
    return response;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.1.0
   */
  @Override
  public int getIoctlCcidEscapeCommandId() {
    return isWindows ? 3500 : 1;
  }
}
