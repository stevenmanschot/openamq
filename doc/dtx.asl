<?xml version="1.0"?>
<class
    name = "dtx"
    handler = "channel"
  >
  work with distributed transactions
 
<doc>
  Distributed transactions provide so-called "2-phase commit".  This
  is slower and more complex than standard transactions but provides
  more assurance that messages will be delivered exactly once.  The
  AMQP/Fast distributed transaction model supports the X-Open XA
  architecture and other distributed transaction implementations.
  The Dtx class assumes that the server has a private communications
  channel (not AMQP/Fast) to a distributed transaction coordinator.
</doc>

<doc name = "grammar">
    dtx                 = C:START S:START-OK
</doc>

<chassis name = "server" implement = "MAY" />
<chassis name = "client" implement = "MAY" />

<!-- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -->

<method name = "start" synchronous = "1">
  start a new distributed transaction
  <doc>
    This method starts a new distributed transaction.  This must be
    the first method on a new channel that uses the distributed
    transaction mode, before any methods that publish or consume
    messages.
  </doc>
  <chassis name = "server" implement = "MAY" />
  <response name = "start-ok" />

  <field name = "dtx identifier" type = "shortstr">
    distributed transaction identifier
    <doc>
      The distributed transaction key. This identifies the transaction
      so that the AMQP/Fast server can coordinate with the distributed
      transaction coordinator.
    </doc>
    <assert check = "notnull" />
  </field>
</method>

<method name = "start-ok" synchronous = "1">
  confirm the start of a new distributed transaction
  <doc>
    This method confirms to the client that the transaction started.
    Note that if a start fails, the server raises a channel exception.
  </doc>
  <chassis name = "client" implement = "MUST" />
</method>

</class>

