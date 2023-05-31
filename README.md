# Keyple Plugin PC/SC Java Library

## Overview

The **Keyple Plugin PC/SC Java Library** is an add-on to allow an application using Keyple to interact with PC/SC 
readers.

## Documentation & Contribution Guide

The full documentation, including the **user guide**, **download information** and **contribution guide**, is available 
on the Keyple website [keyple.org](https://keyple.org).

## API documentation

API documentation & class diagram is available online: 
[eclipse.github.io/keyple-plugin-pcsc-java-lib](https://eclipse.github.io/keyple-plugin-pcsc-java-lib)

## Examples

Examples of implementation are available in the following repository: 
[github.com/eclipse/keyple-java-example](https://github.com/eclipse/keyple-java-example)

##  Limited Observability Support in Java 16+
Please be aware that the current version of the PC/SC Java plugin has limited support for observability when used with 
Java 16+. 
As a result, certain features may not function as expected, and you may encounter issues during usage.

We want to assure you that the Keyple team is actively working on developing an alternative solution to SmartCardIO, 
which will address this limitation and provide improved observability support for Java 16+. 
We apologize for any inconvenience caused and appreciate your patience while we work towards this solution.

In the meantime, if you require full observability support or encounter any issues with the plugin, we recommend 
considering alternative options to smartcard.io such as https://github.com/intarsys/smartcard-io or utilizing an earlier 
version of Java that is fully compatible with the plugin.

We will provide updates on our progress and the availability of the alternative solution as soon as possible.

## About the source code

The code is built with **Gradle** and is compliant with **Java 1.6** in order to address a wide range of applications.