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

import java.security.Provider;
import java.security.Security;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;
import org.eclipse.keyple.core.plugin.PluginIOException;
import org.eclipse.keyple.core.plugin.spi.ObservablePluginSpi;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link PcscPlugin}.
 *
 * @since 2.0.0
 */
final class PcscPluginAdapter implements PcscPlugin, ObservablePluginSpi {

  private static final Logger logger = LoggerFactory.getLogger(PcscPluginAdapter.class);

  private static final int MONITORING_CYCLE_DURATION_MS = 1000;

  private final Map<String, String> protocolRulesMap = new ConcurrentHashMap<>();
  private final Map<String, Pattern> compiledProtocolRulesMap = new ConcurrentHashMap<>();

  private CardTerminals terminals;
  private volatile boolean isCardTerminalsInitialized;

  private final Pattern contactlessReaderIdentificationFilterPattern;
  private final int cardMonitoringCycleDuration;

  /** Constructor. */
  PcscPluginAdapter(
      Provider provider,
      Pattern contactlessReaderIdentificationFilterPattern,
      int cardMonitoringCycleDuration,
      Map<String, String> customProtocolRules) {

    Security.insertProviderAt(provider, 1);

    this.contactlessReaderIdentificationFilterPattern =
        contactlessReaderIdentificationFilterPattern;

    this.cardMonitoringCycleDuration = cardMonitoringCycleDuration;

    // contactless protocols
    protocolRulesMap.put(
        PcscCardCommunicationProtocol.ISO_14443_4.name(),
        PcscCardCommunicationProtocol.ISO_14443_4.getDefaultRule());
    protocolRulesMap.put(
        PcscCardCommunicationProtocol.INNOVATRON_B_PRIME.name(),
        PcscCardCommunicationProtocol.INNOVATRON_B_PRIME.getDefaultRule());
    protocolRulesMap.put(
        PcscCardCommunicationProtocol.MIFARE_ULTRALIGHT.name(),
        PcscCardCommunicationProtocol.MIFARE_ULTRALIGHT.getDefaultRule());
    protocolRulesMap.put(
        PcscCardCommunicationProtocol.MIFARE_CLASSIC_1K.name(),
        PcscCardCommunicationProtocol.MIFARE_CLASSIC_1K.getDefaultRule());
    protocolRulesMap.put(
        PcscCardCommunicationProtocol.MIFARE_CLASSIC_4K.name(),
        PcscCardCommunicationProtocol.MIFARE_CLASSIC_4K.getDefaultRule());
    protocolRulesMap.put(
        PcscCardCommunicationProtocol.ST25_SRT512.name(),
        PcscCardCommunicationProtocol.ST25_SRT512.getDefaultRule());

    // contact protocols
    protocolRulesMap.put(
        PcscCardCommunicationProtocol.ISO_7816_3.name(),
        PcscCardCommunicationProtocol.ISO_7816_3.getDefaultRule());

    if (!customProtocolRules.isEmpty()) {
      logger.info(
          "Customizing protocol identification rules [rules={}]",
          JsonUtil.toJson(customProtocolRules));
      protocolRulesMap.putAll(customProtocolRules);
    } else {
      logger.info("Using default protocol identification rules");
    }

    protocolRulesMap.forEach(
        (name, rule) -> {
          if (rule != null && !rule.isEmpty()) {
            compiledProtocolRulesMap.put(name, Pattern.compile(rule));
          }
        });
  }

  /**
   * Creates a new instance of {@link ReaderSpi} from a {@link CardTerminal}.
   *
   * <p>Note: this method is platform dependent.
   *
   * @param terminal A smartcard.io {@link CardTerminal}.
   * @return A not null reference.
   * @since 2.0.0
   */
  PcscReaderAdapter createReader(CardTerminal terminal) {
    return new PcscReaderAdapter(terminal, this, cardMonitoringCycleDuration);
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public int getMonitoringCycleDuration() {
    return MONITORING_CYCLE_DURATION_MS;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public Set<String> searchAvailableReaderNames() throws PluginIOException {
    Set<String> readerNames = new HashSet<>();
    if (logger.isTraceEnabled()) {
      logger.trace("Searching available reader names");
    }
    for (CardTerminal terminal : getCardTerminalList()) {
      readerNames.add(terminal.getName());
    }
    if (logger.isTraceEnabled()) {
      logger.trace("Readers found [names={}]", JsonUtil.toJson(readerNames));
    }
    return readerNames;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public String getName() {
    return PcscConstants.PLUGIN_NAME;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public Set<ReaderSpi> searchAvailableReaders() throws PluginIOException {
    Set<ReaderSpi> readerSpis = new HashSet<>();
    if (logger.isTraceEnabled()) {
      logger.trace("Searching available readers");
    }
    for (CardTerminal terminal : getCardTerminalList()) {
      ReaderSpi readerSpi = createReader(terminal);
      readerSpis.add(readerSpi);
      logger.info("Reader found [name={}]", readerSpi.getName());
    }
    return readerSpis;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public void onUnregister() {
    /* Nothing to do here in this plugin */
  }

  /**
   * (private) Gets the list of terminals provided by smartcard.io.
   *
   * <p>The aim is to handle the exception possibly raised by the underlying smartcard.io method.
   *
   * @return An empty list if no reader is available.
   * @throws PluginIOException If an error occurs while accessing the list.
   */
  private List<CardTerminal> getCardTerminalList() throws PluginIOException {

    // parse the current readers list to create the ReaderSpi(s) associated with new reader(s)
    try {
      if (!isCardTerminalsInitialized) {
        terminals = TerminalFactory.getDefault().terminals();
        isCardTerminalsInitialized = true;
      }
      return terminals.list();
    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : "";
      if (msg.contains("SCARD_E_NO_READERS_AVAILABLE")) {
        logger.error("No reader available");
      } else if (msg.contains("SCARD_E_NO_SERVICE") || msg.contains("SCARD_E_SERVICE_STOPPED")) {
        logger.error("No running smart card service");
        // the CardTerminals object is no more valid
        isCardTerminalsInitialized = false;
      } else if (msg.contains("SCARD_F_COMM_ERROR")) {
        logger.error("Reader communication error occurred");
      } else {
        throw new PluginIOException("Could not access terminals list", e);
      }
    }
    return new ArrayList<>(0);
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public ReaderSpi searchReader(String readerName) throws PluginIOException {
    if (logger.isTraceEnabled()) {
      logger.trace("Searching reader [reader={}]", readerName);
    }
    for (CardTerminal terminal : getCardTerminalList()) {
      if (readerName.equals(terminal.getName())) {
        if (logger.isTraceEnabled()) {
          logger.trace("Reader found");
        }
        return createReader(terminal);
      }
    }
    if (logger.isTraceEnabled()) {
      logger.trace("Reader not found");
    }
    return null;
  }

  /**
   * Gets the protocol rule associated to the provided protocol.
   *
   * <p>The protocol rule is a regular expression to be applied on the ATR.
   *
   * @param readerProtocol The reader protocol.
   * @return Null if no protocol rules defined for the provided protocol.
   * @since 2.0.0
   */
  String getProtocolRule(String readerProtocol) {
    return protocolRulesMap.get(readerProtocol);
  }

  /**
   * Gets the pre-compiled pattern associated to the provided protocol.
   *
   * @param readerProtocol The reader protocol.
   * @return Null if no rule is defined for the provided protocol.
   * @since 2.0.0
   */
  Pattern getCompiledProtocolRule(String readerProtocol) {
    return compiledProtocolRulesMap.get(readerProtocol);
  }

  /**
   * Attempts to determine the transmission mode of the reader whose name is provided.<br>
   * This determination is made by a test based on a regular expression.
   *
   * @param readerName A string containing the reader name
   * @return True if the reader is contactless, false if not.
   * @since 2.0.0
   */
  boolean isContactless(String readerName) {
    return contactlessReaderIdentificationFilterPattern.matcher(readerName).matches();
  }
}
