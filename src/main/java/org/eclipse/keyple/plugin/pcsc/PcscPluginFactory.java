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

import org.eclipse.keyple.core.common.KeyplePluginExtensionFactory;

/**
 * Factory built by {@link PcscPluginFactoryBuilder}, to be provided to {@link
 * org.eclipse.keyple.core.service.SmartCardService} to register the PC/SC plugin.
 *
 * @since 2.0
 */
public interface PcscPluginFactory extends KeyplePluginExtensionFactory {}
