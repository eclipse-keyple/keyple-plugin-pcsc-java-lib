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
import javax.smartcardio.*;
import jnasmartcardio.Smartcardio;
import org.eclipse.keyple.core.plugin.CardIOException;
import org.eclipse.keyple.core.plugin.ReaderIOException;
import org.eclipse.keyple.core.plugin.TaskCanceledException;
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.CardInsertionWaiterBlockingSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.processing.CardPresenceMonitorBlockingSpi;
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.CardRemovalWaiterBlockingSpi;
import org.eclipse.keyple.core.util.Assert;
import org.eclipse.keyple.core.util.HexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link PcscReaderAdapter}.
 *
 * @since 2.0.0
 */
final class PcscReaderAdapter
    implements PcscReader,
        ConfigurableReaderSpi,
        ObservableReaderSpi,
        CardInsertionWaiterBlockingSpi,
        CardPresenceMonitorBlockingSpi,
        CardRemovalWaiterBlockingSpi {

  private static final Logger logger = LoggerFactory.getLogger(PcscReaderAdapter.class);

  private final CardTerminal communicationTerminal; // For connect/transmit operations
  private final CardTerminal monitoringTerminal; // For waitForCardPresent/Absent operations
  private final String name;
  private final PcscPluginAdapter pluginAdapter;
  private final boolean isWindows;
  private final int cardMonitoringCycleDuration;
  private final byte[] pingApdu = HexUtil.toByteArray("00C0000000"); // GET RESPONSE

  private Boolean isContactless;
  private boolean isModeExclusive;
  private DisconnectionMode disconnectionMode = DisconnectionMode.RESET;
  private String protocol = IsoProtocol.ANY.getValue();

  private Card card;
  private CardChannel channel;
  private String powerOnData = "";
  private boolean isProtocolInnovatronBPrime;

  private final AtomicBoolean isWaitingForInsertion = new AtomicBoolean();
  private final AtomicBoolean isWaitingForRemoval = new AtomicBoolean();

  /**
   * Constructor.
   *
   * @since 2.0.0
   */
  PcscReaderAdapter(
      CardTerminal terminal, PcscPluginAdapter pluginAdapter, int cardMonitoringCycleDuration) {
    this.communicationTerminal = terminal;
    this.pluginAdapter = pluginAdapter;
    this.name = terminal.getName();
    this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    this.cardMonitoringCycleDuration = cardMonitoringCycleDuration;

    // Create a separate PC/SC context for monitoring operations to avoid contention under Linux
    // This is critical because Linux pcsc-lite does not handle concurrent access to a single
    // SCARDCONTEXT as robustly as Windows (see threading differences documentation)
    this.monitoringTerminal = createMonitoringTerminal(terminal.getName());
  }

  /**
   * Returns a CardTerminal dedicated to monitoring operations (waitForCardPresent/Absent).
   *
   * <p>A separate PC/SC context avoids SCARD_E_SHARING_VIOLATION on Linux, where pcsc-lite does not
   * safely handle concurrent access from multiple threads on the same SCARDCONTEXT. Falls back to
   * the communication terminal if a separate context cannot be created.
   *
   * @param terminalName name of the terminal to look up.
   * @return a CardTerminal for monitoring, possibly shared with the communication terminal.
   */
  private CardTerminal createMonitoringTerminal(String terminalName) {
    try {
      // Attempt to create a new TerminalFactory instance to get a separate PC/SC context
      TerminalFactory monitoringFactory = TerminalFactory.getDefault();
      CardTerminals monitoringTerminals = monitoringFactory.terminals();

      // Find the terminal with the same name in the new context
      for (CardTerminal t : monitoringTerminals.list()) {
        if (t.getName().equals(terminalName)) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "[readerExt={}] Separate monitoring context created to improve Linux compatibility",
                terminalName);
          }
          return t;
        }
      }

      // Terminal not found in new context, fall back to same terminal
      logger.warn(
          "[readerExt={}] Terminal not found in monitoring context, falling back to shared context (may cause issues on Linux)",
          terminalName);
      return communicationTerminal;

    } catch (Exception e) {
      // Failed to create separate context, fall back to same terminal
      logger.warn(
          "[readerExt={}] Monitoring context creation failed [reason={}], falling back to shared context (may cause issues on Linux)",
          terminalName,
          e.getMessage());
      return communicationTerminal;
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public boolean isProtocolSupported(String readerProtocol) {
    return pluginAdapter.getProtocolRule(readerProtocol) != null;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void activateProtocol(String readerProtocol) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "[readerExt={}] Protocol activation is a no-op [protocol={}]", getName(), readerProtocol);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void deactivateProtocol(String readerProtocol) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "[readerExt={}] Protocol deactivation is a no-op [protocol={}]",
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
  public boolean isCurrentProtocol(String readerProtocol) {
    String protocolRule = pluginAdapter.getProtocolRule(readerProtocol);
    if (protocolRule != null && !protocolRule.isEmpty()) {
      return Pattern.compile(protocolRule).matcher(powerOnData).matches();
    }
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Checks physical card presence via {@code SCardGetStatusChange}. If present, connects via
   * {@code SCardConnect()} and updates {@link #getPowerOnData()}. If absent, cleans up the card
   * state.
   *
   * @since 3.0.0
   */
  @Override
  public boolean isCardPresent() throws ReaderIOException {
    boolean isPresent;
    try {
      isPresent = communicationTerminal.isCardPresent();
    } catch (CardException | RuntimeException e) {
      throw new ReaderIOException("Failed to check card presence. Reader: " + name, e);
    }
    if (isPresent) {
      try {
        connectCard();
      } catch (CardException e) {
        // Two cases are possible:
        // 1) the card was removed between card presence detection and the card connection attempt,
        // 2) the reader entered a desynchronized state following a card presentation immediately
        //    followed by card removal before application startup (observed with B PRIME cards
        //    and some Paragon ID readers).
        isPresent = false;
      }
    } else {
      disconnectCardSafely();
    }
    return isPresent;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public String getPowerOnData() {
    return powerOnData;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public byte[] transmitApdu(byte[] apduCommandData) throws ReaderIOException, CardIOException {
    byte[] apduResponseData;
    if (channel != null) {
      try {
        apduResponseData = channel.transmit(new CommandAPDU(apduCommandData)).getBytes();
      } catch (CardNotPresentException e) {
        throw new CardIOException("Card not present. APDU transmit failed. Reader: " + name, e);
      } catch (CardException e) {
        if (e.getMessage().contains("CARD")
            || e.getMessage().contains("NOT_TRANSACTED")
            || e.getMessage().contains("INVALID_ATR")) {
          throw new CardIOException(
              "Card communication error. APDU transmit failed. Reader: " + name, e);
        } else {
          throw new ReaderIOException(
              "Reader communication error. APDU transmit failed. Reader: " + name, e);
        }
      } catch (IllegalStateException | IllegalArgumentException e) {
        // card could have been removed prematurely
        throw new CardIOException(
            "Card removed prematurely. APDU transmit failed. Reader: " + name, e);
      }
    } else {
      // could occur if the card was removed
      throw new CardIOException("Card channel is null. Unable to transmit APDU. Reader: " + name);
    }
    return apduResponseData;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public boolean isContactless() {
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
  public void onUnregister() {
    /* Nothing to do here in this reader */
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void onStartDetection() {}

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void onStopDetection() {}

  /**
   * {@inheritDoc}
   *
   * <p>Unpowers the card (SCARD_UNPOWER_CARD) to put it in HALT state. No-op for Innovatron B'Prime
   * cards. The ATR is preserved so the framework can access it while the card remains physically
   * present.
   *
   * @since 3.0.0
   */
  @Override
  public void deselectCard() {
    if (card == null || isProtocolInnovatronBPrime) {
      return;
    }
    try {
      if (card instanceof Smartcardio.JnaCard) {
        ((Smartcardio.JnaCard) card).disconnect(getDisposition(DisconnectionMode.UNPOWER));
        triggerPowerOnCycleSafely();
      } else {
        card.disconnect(true);
      }
    } catch (CardException e) {
      // Card already removed before deselect: treat silently (spec §4.3 pt 5)
      if (logger.isDebugEnabled()) {
        logger.debug(
            "[readerExt={}] Card already removed before deselect [reason={}]",
            name,
            e.getMessage());
      }
    } finally {
      // powerOnData is intentionally kept: card is physically present in HALT state
      card = null;
      channel = null;
    }
  }

  /**
   * Triggers a physical power-on cycle to reset the driver/reader state. After an UNPOWER command,
   * some CCID drivers or readers may remain in a "stale" or "cold" state. This method forces the
   * PC/SC layer to re-energize the slot by initiating an ephemeral connection and immediately
   * releasing it while leaving the power on (SCARD_LEAVE_CARD).
   */
  private void triggerPowerOnCycleSafely() {
    try {
      // Initiating a connection forces the "Power on on demand" mechanism
      // We disconnect with 'false' (SCARD_LEAVE_CARD) to keep the slot powered
      communicationTerminal.connect("*").disconnect(false);
    } catch (CardException ignored) { // NOSONAR
      // If the card is gone, the driver state is naturally reset by the stack
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void waitForCardInsertion() throws TaskCanceledException, ReaderIOException {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "[readerExt={}] Starting waiting card insertion [loopLatencyMs={}]",
          getName(),
          cardMonitoringCycleDuration);
    }

    // activate loop
    isWaitingForInsertion.set(true);

    try {
      while (isWaitingForInsertion.get()) {
        if (monitoringTerminal.waitForCardPresent(cardMonitoringCycleDuration)) {
          try {
            connectCard();
            // card inserted
            if (logger.isTraceEnabled()) {
              logger.trace("[readerExt={}] Card inserted", getName());
            }
            return;
          } catch (CardException e) {
            // Two cases are possible:
            // 1) the card was removed between card presence detection and the card connection
            //    attempt,
            // 2) the reader entered a desynchronized state following a card presentation
            //    immediately followed by card removal before application startup (observed with B
            //    PRIME cards and some Paragon ID readers).
            try {
              Thread.sleep(500);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
          }
        }
        if (Thread.interrupted()) {
          isWaitingForInsertion.set(false);
        }
      }
      if (logger.isTraceEnabled()) {
        logger.trace("[readerExt={}] Waiting card insertion stopped", getName());
      }
    } catch (CardException | RuntimeException e) {
      // here, it is a communication failure with the reader
      throw new ReaderIOException("Failed to wait for a card insertion. Reader: " + name, e);
    }
    throw new TaskCanceledException(
        "The wait for a card insertion task has been cancelled. Reader: " + name,
        new InterruptedException());
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void stopWaitForCardInsertion() {
    isWaitingForInsertion.set(false);
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void monitorCardPresenceDuringProcessing()
      throws ReaderIOException, TaskCanceledException {
    doWaitForCardRemoval(false);
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void stopCardPresenceMonitoringDuringProcessing() {
    stopWaitForCardRemoval();
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void waitForCardRemoval() throws ReaderIOException, TaskCanceledException {
    doWaitForCardRemoval(true);
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void stopWaitForCardRemoval() {
    isWaitingForRemoval.set(false);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default value is {@link SharingMode#SHARED}.
   *
   * @since 2.0.0
   */
  @Override
  public PcscReader setSharingMode(SharingMode sharingMode) {
    Assert.getInstance().notNull(sharingMode, "sharingMode");
    logger.info("[readerExt={}] Set sharing mode [value={}]", getName(), sharingMode.name());
    if (sharingMode == SharingMode.SHARED) {
      // if a card is present, change the mode immediately
      if (card != null) {
        try {
          card.endExclusive();
        } catch (CardException e) {
          throw new IllegalStateException("Failed to disable exclusive mode. Reader: " + name, e);
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
  public PcscReader setContactless(boolean contactless) {
    logger.info("[readerExt={}] Set contactless type [value={}]", getName(), contactless);
    this.isContactless = contactless;
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public PcscReader setIsoProtocol(IsoProtocol isoProtocol) {
    Assert.getInstance().notNull(isoProtocol, "isoProtocol");
    logger.info(
        "[readerExt={}] Set ISO protocol [name={}, value={}]",
        getName(),
        isoProtocol.name(),
        isoProtocol.getValue());
    protocol = isoProtocol.getValue();
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public PcscReader setDisconnectionMode(DisconnectionMode disconnectionMode) {
    Assert.getInstance().notNull(disconnectionMode, "disconnectionMode");
    logger.info(
        "[readerExt={}] Set disconnection mode [value={}]", getName(), disconnectionMode.name());
    this.disconnectionMode = disconnectionMode;
    return this;
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
        Card virtualCard = communicationTerminal.connect("DIRECT");
        response = virtualCard.transmitControlCommand(controlCode, command);
        virtualCard.disconnect(false);
      }
    } catch (CardException e) {
      throw new IllegalStateException("Failed to transmit control command. Reader: " + name, e);
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

  private void connectCard() throws CardException {
    card = communicationTerminal.connect(protocol);
    if (isModeExclusive) {
      card.beginExclusive();
    }
    channel = card.getBasicChannel();
    powerOnData = HexUtil.toHex(card.getATR().getBytes());
    isProtocolInnovatronBPrime =
        isCurrentProtocol(PcscCardCommunicationProtocol.INNOVATRON_B_PRIME.name());
  }

  /**
   * Disconnects the card and resets the card state fields.
   *
   * <p>No-op if no card is connected. UNPOWER and EJECT modes require jnasmartcardio; they fall
   * back to RESET with other providers.
   */
  private void disconnectCardSafely() {
    if (card == null) {
      return;
    }
    try {
      if (card instanceof Smartcardio.JnaCard) {
        ((Smartcardio.JnaCard) card)
            .disconnect(
                getDisposition(
                    isProtocolInnovatronBPrime ? DisconnectionMode.UNPOWER : disconnectionMode));
        if (isProtocolInnovatronBPrime || disconnectionMode == DisconnectionMode.UNPOWER) {
          triggerPowerOnCycleSafely();
        }
      } else {
        // UNPOWER and EJECT are not available outside jnasmartcardio: fall back to RESET
        card.disconnect(disconnectionMode != DisconnectionMode.LEAVE);
      }
    } catch (CardException | RuntimeException e) {
      String msg = e.getMessage() != null ? e.getMessage() : "";
      if (!msg.contains("REMOVED") && !msg.contains("NO_SMARTCARD")) {
        logger.warn("[readerExt={}] Disconnect failed [reason={}]", name, e.getMessage());
      }
    } finally {
      card = null;
      channel = null;
      powerOnData = "";
    }
  }

  /**
   * Maps a DisconnectionMode to the corresponding SCARD_* constant.
   *
   * @param mode The disconnection mode.
   * @return The corresponding SCARD_* value.
   */
  private static int getDisposition(DisconnectionMode mode) {
    switch (mode) {
      case RESET:
        return Smartcardio.JnaCard.SCARD_RESET_CARD;
      case LEAVE:
        return Smartcardio.JnaCard.SCARD_LEAVE_CARD;
      case UNPOWER:
        return Smartcardio.JnaCard.SCARD_UNPOWER_CARD;
      case EJECT:
        return Smartcardio.JnaCard.SCARD_EJECT_CARD;
      default:
        throw new IllegalArgumentException("Unknown DisconnectionMode: " + mode);
    }
  }

  private void doWaitForCardRemoval(boolean allowPolling)
      throws ReaderIOException, TaskCanceledException {
    boolean usePolling = allowPolling && isProtocolInnovatronBPrime;
    if (logger.isTraceEnabled()) {
      logger.trace("[readerExt={}] Starting card removal wait", name);
    }
    isWaitingForRemoval.set(true);
    if (usePolling) {
      awaitCardRemovalByPolling();
    } else {
      awaitCardRemovalBlocking();
    }
    // clean up
    disconnectCardSafely();
    if (logger.isTraceEnabled()) {
      if (!isWaitingForRemoval.get()) {
        logger.trace("[readerExt={}] Card removal wait stopped", name);
      } else {
        logger.trace("[readerExt={}] Card removed", name);
      }
    }
    if (!isWaitingForRemoval.get()) {
      throw new TaskCanceledException("Card removal wait task cancelled. Reader: " + name);
    }
  }

  private void awaitCardRemovalByPolling() throws ReaderIOException {
    try {
      while (isWaitingForRemoval.get()) {
        transmitApdu(pingApdu);
        Thread.sleep(25);
        if (Thread.interrupted()) {
          isWaitingForRemoval.set(false);
        }
      }
    } catch (CardIOException e) {
      if (logger.isTraceEnabled()) {
        logger.trace(
            "[readerExt={}] Expected IOException received while waiting for card removal [reason={}]",
            getName(),
            e.getMessage());
      }
    } catch (InterruptedException e) {
      if (logger.isTraceEnabled()) {
        logger.trace(
            "[readerExt={}] Interrupted while waiting for card removal [reason={}]",
            getName(),
            e.getMessage());
      }
      Thread.currentThread().interrupt();
    }
  }

  private void awaitCardRemovalBlocking() throws ReaderIOException {
    try {
      while (isWaitingForRemoval.get()) {
        if (monitoringTerminal.waitForCardAbsent(cardMonitoringCycleDuration)) {
          return;
        }
        if (Thread.interrupted()) {
          isWaitingForRemoval.set(false);
        }
      }
    } catch (CardException e) {
      throw new ReaderIOException("Failed to wait for the card removal. Reader: " + name, e);
    }
  }
}
