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

import java.util.concurrent.atomic.AtomicBoolean;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import org.eclipse.keyple.core.plugin.ReaderIOException;
import org.eclipse.keyple.core.plugin.TaskCanceledException;
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.WaitForCardInsertionBlockingSpi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * (package-private)<br>
 * Implementation of AbstractPcscReaderAdapter suitable for platforms other than MacOS.
 *
 * @since 2.0
 */
final class PcscReaderAdapter extends AbstractPcscReaderAdapter
    implements WaitForCardInsertionBlockingSpi {

  private static final Logger logger = LoggerFactory.getLogger(PcscReaderAdapter.class);

  // the latency delay value (in ms) determines the maximum time during which the
  // waitForCardPresent blocking functions will execute.
  // This will correspond to the capacity to react to the interrupt signal of
  // the thread.
  private static final long INSERTION_LATENCY = 500;
  private final AtomicBoolean loopWaitCard = new AtomicBoolean();

  /**
   * (package-private)<br>
   *
   * @since 2.0
   */
  PcscReaderAdapter(CardTerminal terminal, AbstractPcscPluginAdapter pluginAdapter) {
    super(terminal, pluginAdapter);
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void waitForCardPresent() throws TaskCanceledException, ReaderIOException {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "{}: start waiting for the insertion of a card in a loop with a latency of {} ms.",
          this.getName(),
          INSERTION_LATENCY);
    }

    // activate loop
    loopWaitCard.set(true);

    try {
      while (loopWaitCard.get()) {
        if (getTerminal().waitForCardPresent(INSERTION_LATENCY)) {
          // card inserted
          if (logger.isTraceEnabled()) {
            logger.trace("{}: card inserted.", this.getName());
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
          this.getName() + ": an error occurred while waiting for a card insertion.", e);
    }
    throw new TaskCanceledException(
        this.getName() + ": the wait for a card insertion task has been cancelled.");
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void stopWaitForCard() {
    if (logger.isTraceEnabled()) {
      logger.trace("{}: stop waiting for card insertion requested.", this.getName());
    }
    loopWaitCard.set(false);
  }
}
