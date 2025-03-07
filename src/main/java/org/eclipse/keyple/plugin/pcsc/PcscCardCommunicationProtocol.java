/* **************************************************************************************
 * Copyright (c) 2025 Calypso Networks Association https://calypsonet.org/
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

/**
 * List of contactless protocols and technologies identifiable through PC/SC readers.
 *
 * <p>Each enum value associates a protocol or technology with a specific ATR pattern. These
 * patterns follow the PC/SC standard Part 3 for contactless card identification. The ATR patterns
 * can identify both physical cards and virtual cards emulated by NFC devices. <br>
 * See <a href="https://pcscworkgroup.com/">PC/SC Workgroup</a> for more details.
 *
 * @since 2.5.0
 */
public enum PcscCardCommunicationProtocol {

  /**
   * Any ISO 14443-4 compliant card or device (both Type A and Type B).
   *
   * <p>According to PC/SC Part 3, ISO 14443-4 devices start with the following historical bytes:
   *
   * <ul>
   *   <li>Type A: 3B8880... or 3B8B80...
   *   <li>Type B: 3B8C80...
   * </ul>
   *
   * <p>Default rule = <b>{@code 3B8880.*|3B8B80.*|3B8C80.*}</b>
   *
   * @since 2.5.0
   */
  ISO_14443_4("3B8880.*|3B8B80.*|3B8C80.*"),

  /**
   * Calypso cards using Innovatron B Prime protocol.
   *
   * <p>According to PC/SC Part 3, B Prime cards use a specific ATR format:
   *
   * <ul>
   *   <li>Initial bytes: 3B8F8001805A0
   *   <li>Historical bytes encoding Calypso data
   *   <li>End marker: 829000
   * </ul>
   *
   * <p>Default rule = <b>{@code 3B8F8001805A0.*.829000}</b>
   *
   * @since 2.5.0
   */
  INNOVATRON_B_PRIME("3B8F8001805A0.*.829000"),

  /**
   * NXP MIFARE Ultralight and UltralightC technologies.
   *
   * <p>According to PC/SC Part 3 Supplemental Document:
   *
   * <ul>
   *   <li>Initial bytes: 3B8F8001804F0CA0000003
   *   <li>Card protocol: 0603 (Type A-3)
   *   <li>Card type: 0003 (for Mifare UL/ULC)
   * </ul>
   *
   * <p>Default rule = <b>{@code 3B8F8001804F0CA0000003060300030.*}</b>
   *
   * @since 2.5.0
   */
  MIFARE_ULTRALIGHT("3B8F8001804F0CA0000003060300030.*"),

  /**
   * STMicroelectronics ST25 memory tags.
   *
   * <p>According to PC/SC Part 3 Supplemental Document:
   *
   * <ul>
   *   <li>Initial bytes: 3B8F8001804F0CA0000003
   *   <li>Card protocol: 0605, 0606, 0607 (Type B-1,2 or 3)
   *   <li>Card type: 0007 (ST25 tag)
   * </ul>
   *
   * <p>Default rule = <b>{@code 3B8F8001804F0CA0000003060(5|6|7)0007.*}</b>
   *
   * @since 2.5.0
   */
  ST25_SRT512("3B8F8001804F0CA0000003060(5|6|7)0007.*"),

  /**
   * ISO7816-3 Card (contact communication protocol)<br>
   * Default rule = <b>{@code 3.*}</b>
   *
   * @since 2.5.0
   */
  ISO_7816_3("3.*");

  private final String defaultRule;

  /**
   * (private-package)<br>
   * Gets the default rule associated to the protocol.
   *
   * @return The regular expression pattern as a String
   * @since 2.0.0
   */
  String getDefaultRule() {
    return defaultRule;
  }

  /**
   * Constructor
   *
   * @param defaultRule The default rule as a regular expression pattern.
   */
  PcscCardCommunicationProtocol(String defaultRule) {
    this.defaultRule = defaultRule;
  }
}
