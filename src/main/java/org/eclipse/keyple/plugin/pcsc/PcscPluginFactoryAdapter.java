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

import java.util.Map;
import org.eclipse.keyple.core.common.CommonsApiProperties;
import org.eclipse.keyple.core.plugin.PluginApiProperties;
import org.eclipse.keyple.core.plugin.spi.PluginFactorySpi;
import org.eclipse.keyple.core.plugin.spi.PluginSpi;

/**
 * (package-private)<br>
 * Factory of {@link PcscPlugin}.
 *
 * @since 2.0
 */
final class PcscPluginFactoryAdapter implements PcscPluginFactory, PluginFactorySpi {

  /**
   * (package-private)<br>
   * The plugin name
   *
   * @since 2.0
   */
  static final String PLUGIN_NAME = "PcscPlugin";

  private final boolean isOsWin;
  private final String contactReaderIdentificationFilter;
  private final String contactlessReaderIdentificationFilter;
  private final Map<String, String> protocolRulesMap;

  /**
   * (package-private)<br>
   * Creates an instance, sets the fields from the factory builder.
   *
   * @since 2.0
   */
  PcscPluginFactoryAdapter(
      String contactReaderIdentificationFilter,
      String contactlessReaderIdentificationFilter,
      Map<String, String> protocolRulesMap) {
    isOsWin = System.getProperty("os.name").toLowerCase().contains("win");
    this.contactReaderIdentificationFilter = contactReaderIdentificationFilter;
    this.contactlessReaderIdentificationFilter = contactlessReaderIdentificationFilter;
    this.protocolRulesMap = protocolRulesMap;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public String getPluginApiVersion() {
    return PluginApiProperties.VERSION;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public String getCommonsApiVersion() {
    return CommonsApiProperties.VERSION;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public String getPluginName() {
    return PLUGIN_NAME;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public PluginSpi getPlugin() {
    AbstractPcscPluginAdapter plugin;
    if (isOsWin) {
      plugin = PcscPluginWinAdapter.getInstance();
    } else {
      plugin = PcscPluginAdapter.getInstance();
    }
    return plugin
        .setContactReaderIdentificationFilter(contactReaderIdentificationFilter)
        .setContactlessReaderIdentificationFilter(contactlessReaderIdentificationFilter)
        .addProtocolRulesMap(protocolRulesMap);
  }
}
