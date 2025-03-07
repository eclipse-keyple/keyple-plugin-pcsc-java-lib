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
   * Any ISO 14443-4 compliant card or device (both Type A and Type B) According to PC/SC Part 3,
   * ISO 14443-4 devices start with the following historical bytes: - Type A: 3B8880... or 3B8B80...
   * - Type B: 3B8C80...
   *
   * <p>Default rule = <b>{@code 3B8880.*|3B8B80.*|3B8C80.*}</b>
   *
   * @since 2.5.0
   */
  ISO_14443_4("3B8880.*|3B8B80.*|3B8C80.*"),

  /**
   * Calypso devices using B Prime protocol According to PC/SC Part 3, B Prime cards use a specific
   * ATR format: - Initial bytes: 3B8F8001805A0 - Historical bytes encoding Calypso data - End
   * marker: 829000
   *
   * <p>Default rule = <b>{@code 3B8F8001805A0.*.829000}</b>
   *
   * @since 2.5.0
   */
  INNOVATRON_B_PRIME("3B8F8001805A0.*.829000"),

  /**
   * NXP MIFARE Ultralight and UltralightC technologies According to PC/SC Part 3 Supplemental
   * Document, memory cards include: - Initial bytes: 3B8F8001804F0CA0000003 - Card type ID: 0603
   * (for memory cards) - Memory card type: 0003 (for Mifare UL/ULC)
   *
   * <p>Default rule = <b>{@code 3B8F8001804F0CA0000003060300030.*}</b>
   *
   * @since 2.5.0
   */
  MIFARE_ULTRALIGHT("3B8F8001804F0CA0000003060300030.*"),

  /**
   * STMicroelectronics ST25 memory tags According to PC/SC Part 3 Supplemental Document: - Initial
   * bytes: 3B8F8001804F0CA0000003 - Card family: 0607 (ST specific ID) - Card type: 0007 (ST25 tag)
   *
   * <p>Default rule = <b>{@code 3B8F8001804F0CA000000306070007.*}</b>
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
