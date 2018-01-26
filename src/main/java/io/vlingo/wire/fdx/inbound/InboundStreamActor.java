// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.fdx.inbound;

import java.nio.ByteBuffer;

import io.vlingo.actors.Actor;
import io.vlingo.actors.Cancellable;
import io.vlingo.actors.Scheduled;
import io.vlingo.actors.Startable;
import io.vlingo.actors.Stoppable;
import io.vlingo.wire.message.RawMessage;
import io.vlingo.wire.node.AddressType;

public class InboundStreamActor extends Actor implements InboundReaderConsumer, InboundStream, Scheduled, Startable, Stoppable {
  private final AddressType addressType;
  private Cancellable cancellable;
  private final InboundStreamInterest interest;
  private final long probeInterval;
  private final InboundReader reader;
  private final InboundStream self;

  public InboundStreamActor(
          final InboundStreamInterest interest,
          final AddressType addressType,
          final InboundReader reader,
          final long probeInterval) {
    this.interest = interest;
    this.addressType = addressType;
    this.reader = reader;
    this.probeInterval = probeInterval;
    this.self = selfAs(InboundStream.class);
  }

  //=========================================
  // InboundStream
  //=========================================

  @Override
  public void respondWith(final InboundClientReference clientReference, final ByteBuffer buffer) {
    try {
      final InboundClientChannel channel = clientReference.reference();
      channel.writeBackResponse(buffer);
    } catch (Exception e) {
      throw new IllegalArgumentException("Exception on client reference channel because: " + e.getMessage(), e);
    }
  }
  
  //=========================================
  // Scheduled
  //=========================================

  @Override
  public void intervalSignal(final Scheduled scheduled, final Object data) {
    reader.probeChannel();
  }

  //=========================================
  // Startable
  //=========================================

  @Override
  public void start() {
    if (isStopped()) return;
    
    logger().log("Inbound stream listening: for '" + reader.inboundName() + "'");
    
    try {
      reader.openFor(this);
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
    
    cancellable = this.stage().scheduler().schedule(selfAs(Scheduled.class), null, 0, probeInterval);
  }

  //=========================================
  // Stoppable
  //=========================================

  @Override
  public void stop() {
    if (cancellable != null) {
      cancellable.cancel();
      cancellable = null;
    }
    
    if (reader != null) {
      reader.close();
    }
    
    super.stop();
  }

  //=========================================
  // InboundReaderConsumer
  //=========================================
  
  @Override
  public void consume(final RawMessage message, final InboundClientChannel clientChannel) {
    interest.handleInboundStreamMessage(addressType, RawMessage.copy(message), new InboundClientChannelResponder(self, clientChannel));
  }
}
