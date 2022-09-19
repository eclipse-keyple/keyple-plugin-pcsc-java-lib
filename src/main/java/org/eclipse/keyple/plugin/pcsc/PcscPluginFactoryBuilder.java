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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.keyple.core.util.Assert;

/**
 * Builds instances of {@link PcscPluginFactory} from values configured by the setters.
 *
 * <p>The Builder checks if a value configured by a setter satisfies the syntax requirements defined
 * by the {@link PcscPluginFactoryAdapter} class.
 *
 * <p>Note: all setters of this class are optional.<br>
 * It is possible to assign later a protocol type at the reader level using the method {@link
 * PcscReader#setContactless(boolean)}. <br>
 * A set of default protocol identification rules is also proposed.
 *
 * @see PcscSupportedContactProtocol
 * @see PcscSupportedContactlessProtocol
 * @since 2.0.0
 */
public final class PcscPluginFactoryBuilder {

  private PcscPluginFactoryBuilder() {}

  /**
   * Creates builder to build a {@link PcscPluginFactory}.
   *
   * @return created builder
   * @since 2.0.0
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder to build a {@link PcscPluginFactory}.
   *
   * @since 2.0.0
   */
  public static class Builder {

    public static final String DEFAULT_CONTACTLESS_READER_FILTER =
        "(?i).*(contactless|ask logo|acs acr122).*";
    private Pattern contactlessReaderIdentificationFilterPattern =
        Pattern.compile(DEFAULT_CONTACTLESS_READER_FILTER);
    private final Map<String, String> protocolRulesMap;

    /**
     * (private)<br>
     * Constructs an empty Builder. The default value of all strings is null, the default value of
     * the map is an empty map.
     */
    private Builder() {
      protocolRulesMap = new HashMap<String, String>();
    }

    /**
     * Sets a filter based on regular expressions to make the plugin able to identify a contact
     * reader from its name.
     *
     * <p>Readers whose names match the provided regular expression will be considered contact type
     * readers.
     *
     * <p>For example, the string ".*less.*" could identify all readers having "less" in their name
     * as contactless readers.
     *
     * <p>Names are not always as explicit, so it is sometimes better to test the brand and model.
     * <br>
     * Commonly used contact readers include "Cherry TC" or "Identive".<br>
     * Thus, an application using these readers should call this method with {@code ".*(Cherry
     * TC|Identive).*"} as an argument.
     *
     * @param contactReaderIdentificationFilter A string a regular expression.
     * @return This builder.
     * @since 2.0.0
     * @deprecated Useless method that will be removed soon, see {@link
     *     #useContactlessReaderIdentificationFilter(String)}
     */
    @Deprecated
    public Builder useContactReaderIdentificationFilter(String contactReaderIdentificationFilter) {
      return this;
    }

    /**
     * Overwrites the default filter with the provided filter based on regular expressions to make
     * the plugin able to identify a contact reader from its name.
     *
     * <p>Readers whose names match the provided regular expression will be considered contactless
     * type readers.
     *
     * <p>The default value is {@value #DEFAULT_CONTACTLESS_READER_FILTER}
     *
     * @param contactlessReaderIdentificationFilter A regular expression.
     * @return This builder.
     * @throws IllegalArgumentException If the provided string is null, empty or invalid.
     * @see #useContactReaderIdentificationFilter(String)
     * @since 2.0.0
     */
    public Builder useContactlessReaderIdentificationFilter(
        String contactlessReaderIdentificationFilter) {
      Assert.getInstance()
          .notEmpty(contactlessReaderIdentificationFilter, "contactlessReaderIdentificationFilter");
      try {
        this.contactlessReaderIdentificationFilterPattern =
            Pattern.compile(contactlessReaderIdentificationFilter);
      } catch (Exception e) {
        throw new IllegalArgumentException("Bad regular expression.", e);
      }
      return this;
    }

    /**
     * Updates a protocol identification rule.
     *
     * <p>A protocol rule is a regular expression contained in a String.
     *
     * <ul>
     *   <li>If a rule already exists for the provided protocol, it is replaced.
     *   <li>If no rule exists for the provided protocol, it is added.
     *   <li>If the rule is null, the protocol is disabled.
     * </ul>
     *
     * @param readerProtocolName A not empty String.
     * @param protocolRule null to disable the protocol.
     * @return This builder.
     * @throws IllegalArgumentException If one of the argument is null or empty
     * @since 2.0.0
     */
    public Builder updateProtocolIdentificationRule(
        String readerProtocolName, String protocolRule) {
      Assert.getInstance().notEmpty(readerProtocolName, "readerProtocolName");
      if (protocolRule == null) {
        // disable the protocol by defining a regex that always fails.
        protocolRulesMap.put(readerProtocolName, "X");
      } else {
        protocolRulesMap.put(readerProtocolName, protocolRule);
      }
      return this;
    }

    /**
     * Returns an instance of PcscPluginFactory created from the fields set on this builder.
     *
     * <p>The type of reader is determined using a regular expression applied to its name. <br>
     * The default regular expression is {@value DEFAULT_CONTACTLESS_READER_FILTER}.<br>
     * Readers that do not match this regular expression are considered contact type. <br>
     * It is possible to redefine the contactless reader filter via the method {@link
     * #useContactlessReaderIdentificationFilter(String)}.
     *
     * @return A {@link PcscPluginFactory}
     * @since 2.0.0
     */
    public PcscPluginFactory build() {
      return new PcscPluginFactoryAdapter(
          contactlessReaderIdentificationFilterPattern, protocolRulesMap);
    }
  }
}
