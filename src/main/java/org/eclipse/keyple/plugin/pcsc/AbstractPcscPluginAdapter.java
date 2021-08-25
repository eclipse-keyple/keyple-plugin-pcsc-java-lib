/* **************************************************************************************
 * Copyright (c) 2018 Calypso Networks Association https://calypsonet.org/
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import org.eclipse.keyple.core.plugin.PluginIOException;
import org.eclipse.keyple.core.plugin.spi.ObservablePluginSpi;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * (package-private) <br>
 * Abstract class for all PC/SC plugin adapters.
 *
 * @since 2.0.0
 */
abstract class AbstractPcscPluginAdapter implements PcscPlugin, ObservablePluginSpi {

  private static final Logger logger = LoggerFactory.getLogger(AbstractPcscPluginAdapter.class);
  private static final int MONITORING_CYCLE_DURATION_MS = 1000;

  private static final Map<String, String> protocolRulesMap =
      new ConcurrentHashMap<String, String>();

  // initializes the protocol rules map with default values
  static {
    // contactless protocols
    protocolRulesMap.put(
        PcscSupportedContactlessProtocol.ISO_14443_4.name(),
        PcscSupportedContactlessProtocol.ISO_14443_4.getDefaultRule());
    protocolRulesMap.put(
        PcscSupportedContactlessProtocol.INNOVATRON_B_PRIME_CARD.name(),
        PcscSupportedContactlessProtocol.INNOVATRON_B_PRIME_CARD.getDefaultRule());
    protocolRulesMap.put(
        PcscSupportedContactlessProtocol.MIFARE_ULTRA_LIGHT.name(),
        PcscSupportedContactlessProtocol.MIFARE_ULTRA_LIGHT.getDefaultRule());
    protocolRulesMap.put(
        PcscSupportedContactlessProtocol.MIFARE_CLASSIC.name(),
        PcscSupportedContactlessProtocol.MIFARE_CLASSIC.getDefaultRule());
    protocolRulesMap.put(
        PcscSupportedContactlessProtocol.MIFARE_DESFIRE.name(),
        PcscSupportedContactlessProtocol.MIFARE_DESFIRE.getDefaultRule());
    protocolRulesMap.put(
        PcscSupportedContactlessProtocol.MEMORY_ST25.name(),
        PcscSupportedContactlessProtocol.MEMORY_ST25.getDefaultRule());

    // contacts protocols
    protocolRulesMap.put(
        PcscSupportedContactProtocol.ISO_7816_3.name(),
        PcscSupportedContactProtocol.ISO_7816_3.getDefaultRule());
    protocolRulesMap.put(
        PcscSupportedContactProtocol.ISO_7816_3_T0.name(),
        PcscSupportedContactProtocol.ISO_7816_3_T0.getDefaultRule());
    protocolRulesMap.put(
        PcscSupportedContactProtocol.ISO_7816_3_T1.name(),
        PcscSupportedContactProtocol.ISO_7816_3_T1.getDefaultRule());
  }

  private final String name;
  private String contactReaderIdentificationFilter;
  private String contactlessReaderIdentificationFilter;

  /**
   * (package-private)<br>
   * Common constructor for all Pcsc plugin adapters instances.
   *
   * @param name The name of the plugin.
   * @since 2.0.0
   */
  AbstractPcscPluginAdapter(String name) {
    this.name = name;
  }

  /**
   * (package-private)<br>
   * Sets the filter to identify contact readers.
   *
   * @param contactReaderIdentificationFilter A regular expression, null if the filter is not set.
   * @return The object instance.
   * @since 2.0.0
   */
  final AbstractPcscPluginAdapter setContactReaderIdentificationFilter(
      String contactReaderIdentificationFilter) {
    if (logger.isTraceEnabled()) {
      if (contactReaderIdentificationFilter != null) {
        logger.trace(
            "{}: contact reader identification filter set to {}",
            getName(),
            contactReaderIdentificationFilter);
      } else {
        logger.trace("{}: no contact reader identification filter set", getName());
      }
    }
    this.contactReaderIdentificationFilter = contactReaderIdentificationFilter;
    return this;
  }

  /**
   * (package-private)<br>
   * Sets the filter to identify contactless readers.
   *
   * @param contactlessReaderIdentificationFilter A regular expression, null if the filter is not
   *     set.
   * @return The object instance.
   * @since 2.0.0
   */
  final AbstractPcscPluginAdapter setContactlessReaderIdentificationFilter(
      String contactlessReaderIdentificationFilter) {
    if (logger.isTraceEnabled()) {
      if (contactlessReaderIdentificationFilter != null) {
        logger.trace(
            "{}: contactless reader identification filter set to {}",
            getName(),
            contactlessReaderIdentificationFilter);
      } else {
        logger.trace("{}: no contactless reader identification filter set", getName());
      }
    }
    this.contactlessReaderIdentificationFilter = contactlessReaderIdentificationFilter;
    return this;
  }

  /**
   * (package-private)<br>
   * Adds a map of rules to the current default map.
   *
   * <p>Already existing items are overridden, new items are added.
   *
   * @param protocolRulesMap The regex based filter.
   * @return The object instance.
   * @since 2.0.0
   */
  final AbstractPcscPluginAdapter addProtocolRulesMap(Map<String, String> protocolRulesMap) {
    if (logger.isTraceEnabled()) {
      if (!protocolRulesMap.isEmpty()) {
        logger.trace(
            "{}: protocol identification rules updated with {}",
            getName(),
            JsonUtil.toJson(protocolRulesMap));
      } else {
        logger.trace("{}: using default protocol identification rules.", getName());
      }
    }
    AbstractPcscPluginAdapter.protocolRulesMap.putAll(protocolRulesMap);
    return this;
  }

  /**
   * (package-private)<br>
   * Gets the protocol rule associated to the provided protocol.
   *
   * <p>The protocol rule is a regular expression to be applied on the ATR.
   *
   * @param readerProtocol The reader protocol.
   * @return Null if no protocol rules defined for the provided protocol.
   * @since 2.0.0
   */
  final String getProtocolRule(String readerProtocol) {
    return protocolRulesMap.get(readerProtocol);
  }

  /**
   * (package-private)<br>
   * Creates a new instance of {@link ReaderSpi} from a {@link CardTerminal}.
   *
   * <p>Note: this method is platform dependent.
   *
   * @param terminal A smartcard.io {@link CardTerminal}.
   * @return A not null reference.
   * @since 2.0.0
   */
  abstract ReaderSpi createReader(CardTerminal terminal);

  /**
   * (package-private)<br>
   * Gets a new {@link CardTerminals} object encapsulating the available terminals.
   *
   * <p>Note: this method is platform dependent.
   *
   * @return A {@link CardTerminals} reference
   * @since 2.0.0
   */
  abstract CardTerminals getCardTerminals();

  /**
   * (package-private)<br>
   * Attempts to determine the transmission mode of the reader whose name is provided.<br>
   * This determination is made by a test based on the regular expressions.
   *
   * @param readerName A string containing the reader name
   * @return True if the reader is contactless, false if not.
   * @throws IllegalStateException If the mode of transmission could not be determined
   * @throws PatternSyntaxException If the expression's syntax is invalid
   * @since 2.0.0
   */
  final boolean isContactless(String readerName) {

    Pattern p;
    p = Pattern.compile(contactReaderIdentificationFilter);
    if (p.matcher(readerName).matches()) {
      return false;
    }
    p = Pattern.compile(contactlessReaderIdentificationFilter);
    if (p.matcher(readerName).matches()) {
      return true;
    }
    throw new IllegalStateException(
        "Unable to determine the transmission mode for reader " + readerName);
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
    CardTerminals terminals = getCardTerminals();
    try {
      return terminals.list();
    } catch (CardException e) {
      if (e.getCause().toString().contains("SCARD_E_NO_READERS_AVAILABLE")) {
        logger.error("No reader available.");
      } else {
        throw new PluginIOException("Could not access terminals list", e);
      }
    }
    return new ArrayList<CardTerminal>(0);
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
  public final Set<ReaderSpi> searchAvailableReaders() throws PluginIOException {
    Set<ReaderSpi> readerSpis = new HashSet<ReaderSpi>();

    for (CardTerminal terminal : getCardTerminalList()) {
      readerSpis.add(createReader(terminal));
    }
    if (logger.isTraceEnabled()) {
      logger.trace("{}: available readers {}", this.getName(), JsonUtil.toJson(readerSpis));
    }
    return readerSpis;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final void onUnregister() {
    /* Nothing to do here in this plugin */
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final int getMonitoringCycleDuration() {
    return MONITORING_CYCLE_DURATION_MS;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final Set<String> searchAvailableReaderNames() throws PluginIOException {
    Set<String> readerNames = new HashSet<String>();

    for (CardTerminal terminal : getCardTerminalList()) {
      readerNames.add(terminal.getName());
    }
    if (logger.isTraceEnabled()) {
      logger.trace("{}: available readers names {}", this.getName(), JsonUtil.toJson(readerNames));
    }
    return readerNames;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public final ReaderSpi searchReader(String readerName) throws PluginIOException {
    if (logger.isTraceEnabled()) {
      logger.trace("{}: search reader: {}", this.getName(), readerName);
    }
    for (CardTerminal terminal : getCardTerminalList()) {
      if (readerName.equals(terminal.getName())) {
        if (logger.isTraceEnabled()) {
          logger.trace("{}: reader: {} found.", this.getName(), readerName);
        }
        return createReader(terminal);
      }
    }
    if (logger.isTraceEnabled()) {
      logger.trace("{}: reader: {} not found.", this.getName(), readerName);
    }
    return null;
  }
}
