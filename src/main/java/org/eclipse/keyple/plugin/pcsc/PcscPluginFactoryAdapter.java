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

import java.security.Provider;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.keyple.core.common.CommonApiProperties;
import org.eclipse.keyple.core.plugin.PluginApiProperties;
import org.eclipse.keyple.core.plugin.spi.PluginFactorySpi;
import org.eclipse.keyple.core.plugin.spi.PluginSpi;

/**
 * Factory of {@link PcscPlugin}.
 *
 * @since 2.0.0
 */
final class PcscPluginFactoryAdapter implements PcscPluginFactory, PluginFactorySpi {

  static final String PLUGIN_NAME = "PcscPlugin";

  /**
   * The plugin name
   *
   * @since 2.0.0
   */
  private final Pattern contactlessReaderIdentificationFilterPattern;

  private final Map<String, String> protocolRulesMap;
  private final int cardMonitoringCycleDuration;
  private final Provider provider;

  /**
   * Creates an instance, sets the fields from the factory builder.
   *
   * @since 2.0.0
   */
  PcscPluginFactoryAdapter(
      Provider provider,
      Pattern contactlessReaderIdentificationFilterPattern,
      Map<String, String> protocolRulesMap,
      int cardMonitoringCycleDuration) {
    this.provider = provider;
    this.contactlessReaderIdentificationFilterPattern =
        contactlessReaderIdentificationFilterPattern;
    this.protocolRulesMap = protocolRulesMap;
    this.cardMonitoringCycleDuration = cardMonitoringCycleDuration;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public String getPluginApiVersion() {
    return PluginApiProperties.VERSION;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public String getCommonApiVersion() {
    return CommonApiProperties.VERSION;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public String getPluginName() {
    return PLUGIN_NAME;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public PluginSpi getPlugin() {
    PcscPluginAdapter plugin = PcscPluginAdapter.getInstance();
    return plugin
        .setProvider(provider)
        .setContactlessReaderIdentificationFilterPattern(
            contactlessReaderIdentificationFilterPattern)
        .addProtocolRulesMap(protocolRulesMap)
        .setCardMonitoringCycleDuration(cardMonitoringCycleDuration);
  }
}
