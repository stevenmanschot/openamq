<?xml version="1.0"?>
<protocol
    name     = "amq"
    comment  = "AMQ protocol 0.9 alpha"
    script   = "asl_gen"
    target   = "stdc"
    >

<inherit name = "asl_base" />
<include filename = "amq_access.asl" />
<include filename = "amq_exchange.asl" />
<include filename = "amq_queue.asl" />
<include filename = "amq_basic.asl" />
<include filename = "amq_jms.asl" />

<option name = "protocol_name"     value = "AMQP" />
<option name = "protocol_port"     value = "7654" />
<option name = "protocol_class"    value = "1" />
<option name = "protocol_instance" value = "1" />
<option name = "protocol_major"    value = "0" />
<option name = "protocol_minor"    value = "9" />

<!-- Standard field domains -->
  <domain name = "access ticket" type = "short">
    access ticket granted by server
    <doc>
    An access ticket granted by the server for a certain set of access
    rights within a specific realm. Access tickets are valid within the
    channel where they were created, and expire when the channel closes.
    </doc>
    <assert check = "ne" value = "0"/>
  </domain>

  <domain name = "exchange name" type = "shortstr">
    exchange name
    <doc>
      The exchange name is a client-selected string that identifies
      the exchange for publish methods.  Exchange names may consist
      of any mixture of digits, letters, and underscores.  Exchange
      names predefined by the server are prefixed by "$", which is
      not a valid character in client-declared exchanges.  Exchange
      names are scoped by the virtual host.
    </doc>
    <assert check = "length" value = "127" />
  </domain>

  <domain name = "queue scope" type = "shortstr">
    queue name scope
    <doc>
      The queue scope is a client-selected string that allows a
      separation of queues by function.  The server may define
      standard scopes that provide templating functionality -
      e.g. a default exchange binding for queues declared in a
      specific scope.  Two scopes can each contain a queue with
      the same name - these would be two distinct queues.  Scope
      names may consist of any mixture of digits, letters, and
      underscores.  Scope names predeclared by the server start
      with "$", not a valid character in client-declared scopes.
      Scopes are themselves scoped by the virtual host.
    </doc>
    <assert check = "length" value = "127" />
  </domain>

  <domain name = "queue name" type = "shortstr">
    queue name
    <doc>
    The queue name identifies the queue within the scope.  Queue
    names may consist of any mixture of digits, letters, and
    underscores.  Queue names predefined by the server start with
    "$", not a valid character in client-declared queues.
    </doc>
    <assert check = "length" value = "127" />
  </domain>

  <domain name = "no local" type = "bit">
    do not deliver own messages
    <doc>
    If the no-local field is set the server will not send messages to
    the client that published them.
    </doc>
  </domain>

  <domain name = "auto ack" type = "bit">
    no acknowledgement needed
    <doc>
      If this field is set the server does not expect acknowledgments
      for messages.  That is, when a message is delivered to the client
      the server automatically and silently acknowledges it on behalf
      of the client.  This functionality increases performance but at
      the cost of reliability.  Messages can get lost if a client dies
      before it can deliver them to the application.
    </doc>
  </domain>

  <domain name = "consumer tag" type = "short">
    server-assigned consumer tag
    <doc>
      The server-assigned and channel-specific consumer tag
    </doc>
    <doc name = "rule">
      The consumer tag is valid only within the channel from which the
      consumer was created. I.e. a client may not create a consumer in
      one channel and then cancel it in another.
    </doc>
    <assert check = "ne" value = "0" />
  </domain>

  <domain name = "delivery tag" type = "longlong">
    server-assigned delivery tag
    <doc>
      The server-assigned and channel-specific delivery tag
    </doc>
    <doc name = "rule">
      The delivery tag is valid only within the channel from which the
      message was received.  I.e. a client may not receive a message on
      one channel and then acknowledge it on another.
    </doc>
    <doc name = "rule">
      The server MUST NOT use a zero value for delivery tags.  Zero is
      reserved for client use, meaning "all messages so far received".
    </doc>
  </domain>

  <domain name = "redelivered" type = "bit">
    message is being redelivered
    <doc>
      This indicates that the message has been previously delivered to
      this or another client.
    </doc>
    <doc name = "rule">
      The server SHOULD try to signal redelivered messages when it can.
      When redelivering a message that was not successfully acknowledged,
      the server SHOULD deliver it to the original client if possible.
    </doc>
    <doc name = "rule">
      The client MUST NOT rely on the redelivered field but MUST take it
      as a hint that the message may already have been processed.  A
      fully robust client must be able to track duplicate received messages
      on non-transacted, and locally-transacted channels.
    </doc>
  </domain>
</protocol>