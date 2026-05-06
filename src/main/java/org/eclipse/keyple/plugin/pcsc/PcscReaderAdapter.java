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
  private Card card;
  private CardChannel channel;
  private Boolean isContactless;
  private String protocol = IsoProtocol.ANY.getValue();
  private boolean isModeExclusive;
  private DisconnectionMode disconnectionMode = DisconnectionMode.RESET;
  private String powerOnData = "";

  private final AtomicBoolean isWaitingForInsertion = new AtomicBoolean();
  private final AtomicBoolean isWaitingForRemoval = new AtomicBoolean();
  private boolean isObservationActive;
  private boolean isProtocolInnovatronBPrime;

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
   * Creates a separate CardTerminal instance for monitoring operations using a dedicated PC/SC
   * context.
   *
   * <p>Under Linux with pcsc-lite, sharing the same SCARDCONTEXT between blocking monitoring calls
   * (waitForCardPresent/Absent) and communication operations (transmit) can cause thread contention
   * and SCARD_E_SHARING_VIOLATION errors due to the self-pipe trick mechanism used for
   * cancellation.
   *
   * <p>This method attempts to create a new TerminalFactory instance to obtain a separate context.
   * If this fails (e.g., on older JRE versions or with certain security providers), it falls back
   * to using the same terminal, which may cause issues on Linux but will still work on Windows.
   *
   * @param terminalName The name of the terminal to create a monitoring instance for.
   * @return A CardTerminal instance for monitoring, either with a separate context or the same one.
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
          "[readerExt={}] Could not find terminal in separate context, using shared context (may cause issues on Linux)",
          terminalName);
      return communicationTerminal;

    } catch (Exception e) {
      // Failed to create separate context, fall back to same terminal
      logger.warn(
          "[readerExt={}] Could not create separate monitoring context [reason={}], using shared context (may cause issues on Linux)",
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
  public void waitForCardInsertion() throws TaskCanceledException, ReaderIOException {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "[readerExt={}] Starting waiting card insertion [loopLatencyMs={}]",
          getName(),
          cardMonitoringCycleDuration);
    }

    // activate loop
    isWaitingForInsertion.set(true);

    boolean interrupted = false;
    try {
      while (isWaitingForInsertion.get()) {
        if (monitoringTerminal.waitForCardPresent(cardMonitoringCycleDuration)) {
          // card inserted
          if (logger.isTraceEnabled()) {
            logger.trace("[readerExt={}] Card inserted", getName());
          }
          try {
            connectCard();
            return;
          } catch (CardNotPresentException e) {
          }
        }
        if (Thread.interrupted()) {
          interrupted = true;
          break;
        }
      }
      if (logger.isTraceEnabled()) {
        logger.trace("[readerExt={}] Waiting card insertion stopped", getName());
      }
    } catch (CardException e) {
      // here, it is a communication failure with the reader
      throw new ReaderIOException("Failed to wait for a card insertion. Reader: " + name, e);
    }
    if (interrupted) {
      throw new TaskCanceledException(
          "The wait for a card insertion task has been cancelled (interrupted). Reader: " + name,
          new InterruptedException());
    }
    throw new TaskCanceledException(
        "The wait for a card insertion task has been cancelled. Reader: " + name);
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
          "[readerExt={}] Activating protocol takes no action [protocol={}]",
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
  public void deactivateProtocol(String readerProtocol) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "[readerExt={}] de-activating protocol takes no action [protocol={}]",
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
  public void onStartDetection() {
    isObservationActive = true;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void onStopDetection() {
    isObservationActive = false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Sends S(DESELECT) to put the PICC in HALT state (via SCARD_UNPOWER_CARD). The ATR cache is
   * preserved so the framework can still log it after deselection while the card remains physically
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
        // reset the driver state to avoid stale reader state after UNPOWER on some drivers
        try {
          communicationTerminal.connect("*").disconnect(false);
        } catch (CardException ignored) {
          // NOP
        }
      } else {
        card.disconnect(true);
      }
    } catch (CardException e) {
      // Card already removed before deselect: treat silently (spec §4.3 pt 5)
      if (logger.isDebugEnabled()) {
        logger.debug(
            "[readerExt={}] deselectCard: card already removed [reason={}]", name, e.getMessage());
      }
    } finally {
      // powerOnData is intentionally kept: card is physically present in HALT state
      card = null;
      channel = null;
    }
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
   * Closes the physical channel with the card.
   *
   * <p>No-op if the channel is already closed. For contactless readers, {@link #deselectCard()}
   * (SCARD_UNPOWER_CARD) should be called first for a protocol-clean HALT transition; this method
   * is then a no-op in normal flow.
   *
   * <p>UNPOWER and EJECT modes require jnasmartcardio; they silently fall back to RESET with other
   * providers.
   *
   * @throws ReaderIOException If the communication with the reader has failed.
   * @since 2.0.0
   */
  private void disconnectCard() throws ReaderIOException {
    if (card == null) {
      return;
    }
    try {
      if (card instanceof Smartcardio.JnaCard) {
        ((Smartcardio.JnaCard) card)
            .disconnect(
                getDisposition(
                    isProtocolInnovatronBPrime ? DisconnectionMode.UNPOWER : disconnectionMode));
      } else {
        // UNPOWER and EJECT are not available outside jnasmartcardio: fall back to RESET
        card.disconnect(disconnectionMode != DisconnectionMode.LEAVE);
      }
    } catch (CardException e) {
      String msg = e.getMessage() != null ? e.getMessage() : "";
      if (!msg.contains("REMOVED") && !msg.contains("NO_SMARTCARD")) {
        throw new ReaderIOException("Failed to disconnect the card. Reader: " + name, e);
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

  /**
   * {@inheritDoc}
   *
   * <p>Checks whether a card is present and activates communication with it.
   *
   * <p>When the channel is closed, attempts a full {@code SCardConnect()} to perform anti-collision
   * for contactless readers. On success the channel is marked open and {@link #getPowerOnData()}
   * returns the ATR. When the channel is open, checks physical presence via {@code
   * SCardGetStatusChange} and calls {@link #disconnectCard()} internally if the card is no longer
   * present.
   *
   * @since 3.0.0
   */
  @Override
  public boolean isCardPresent() throws ReaderIOException {
    try {
      boolean isPresent = communicationTerminal.isCardPresent();
      if (isPresent) {
        if (card == null) {
          // channel closed: attempt connection (performs anti-collision for contactless readers)
          try {
            connectCard();
          } catch (CardNotPresentException e) {
            isPresent = false;
          }
        }
      } else {
        try {
          disconnectCard();
        } catch (ReaderIOException ignored) {
          // card already gone; flags are reset in disconnectCard finally block
        }
      }
      return isPresent;
    } catch (CardException e) {
      throw new ReaderIOException("Failed to check card presence. Reader: " + name, e);
    }
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
        throw new CardIOException(
            "Card is not present. Unable to transmit APDU. Reader: " + name, e);
      } catch (CardException e) {
        if (e.getMessage().contains("CARD")
            || e.getMessage().contains("NOT_TRANSACTED")
            || e.getMessage().contains("INVALID_ATR")) {
          throw new CardIOException(
              "Failed to communicate with card. Unable to transmit APDU. Reader: " + name, e);
        } else {
          throw new ReaderIOException(
              "Failed to communicate with card reader. Unable to transmit APDU. Reader: " + name,
              e);
        }
      } catch (IllegalStateException | IllegalArgumentException e) {
        // card could have been removed prematurely
        throw new CardIOException(
            "Card could have been removed prematurely. Unable to transmit APDU. Reader: " + name,
            e);
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
  public void monitorCardPresenceDuringProcessing()
      throws ReaderIOException, TaskCanceledException {
    doWaitForCardRemoval(!isProtocolInnovatronBPrime);
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

  private void doWaitForCardRemoval(boolean allowPolling)
      throws ReaderIOException, TaskCanceledException {
    if (logger.isTraceEnabled()) {
      logger.trace("[readerExt={}] Starting waiting card removal", name);
    }
    isWaitingForRemoval.set(true);
    if (allowPolling && isProtocolInnovatronBPrime) {
      awaitCardRemovalByPolling();

    } else {
      awaitCardRemovalBlocking();
    }
    if (logger.isTraceEnabled()) {
      if (!isWaitingForRemoval.get()) {
        logger.trace("[readerExt={}] Waiting card removal stopped", name);
      } else {
        logger.trace("[readerExt={}] Card removed", name);
      }
    }
    if (!isWaitingForRemoval.get()) {
      throw new TaskCanceledException(
          "The wait for the card removal task has been cancelled. Reader: " + name);
    }
  }

  private void awaitCardRemovalByPolling() {
    try {
      while (isWaitingForRemoval.get()) {
        transmitApdu(pingApdu);
        Thread.sleep(25);
        if (Thread.interrupted()) {
          return;
        }
      }
    } catch (CardIOException | ReaderIOException e) {
      if (logger.isTraceEnabled()) {
        logger.trace(
            "[readerExt={}] Expected IOException received while waiting for card removal [reason={}]",
            getName(),
            e.getMessage());
      }
    } catch (InterruptedException e) {
      if (logger.isTraceEnabled()) {
        logger.trace(
            "[readerExt={}] InterruptedException received while waiting for card removal: {}",
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
          return;
        }
      }
    } catch (CardException e) {
      throw new ReaderIOException("Failed to wait for the card removal. Reader: " + name, e);
    }
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
        "[readerExt={}] Set ISO protocol [protocol={}, value={}]",
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
   * @since 2.0.0
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
   * @since 2.0.0
   */
  @Override
  public int getIoctlCcidEscapeCommandId() {
    return isWindows ? 3500 : 1;
  }
}
